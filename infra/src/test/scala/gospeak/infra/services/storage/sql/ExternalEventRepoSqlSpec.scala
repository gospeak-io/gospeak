package gospeak.infra.services.storage.sql

import gospeak.core.domain.{CommonEvent, Event}
import gospeak.infra.services.storage.sql.ExternalEventRepoSqlSpec._
import gospeak.infra.services.storage.sql.testingutils.RepoSpec
import gospeak.infra.services.storage.sql.testingutils.RepoSpec.mapFields

class ExternalEventRepoSqlSpec extends RepoSpec {
  describe("ExternalEventRepoSql") {
    it("should handle crud operations") {
      val (user, group, ctx) = createOrga().unsafeRunSync()
      externalEventRepo.list(params)(ctx).unsafeRunSync().items shouldBe List()
      val event = externalEventRepo.create(externalEventData1)(ctx).unsafeRunSync()
      externalEventRepo.list(params)(ctx).unsafeRunSync().items shouldBe List(event)

      externalEventRepo.edit(event.id)(externalEventData2)(ctx).unsafeRunSync()
      externalEventRepo.list(params)(ctx).unsafeRunSync().items.map(_.data) shouldBe List(externalEventData2)
      // no delete...
    }
    it("should select a page") {
      val (user, group, ctx) = createOrga().unsafeRunSync()
      val event1 = externalEventRepo.create(externalEventData1.copy(kind = Event.Kind.Meetup, name = Event.Name("aaa"), videos = Some(urlVideos), start = Some(nowLDT.plusDays(1))))(ctx).unsafeRunSync()
      val event2 = externalEventRepo.create(externalEventData2.copy(kind = Event.Kind.Conference, videos = None, start = Some(nowLDT.minusDays(1))))(ctx).unsafeRunSync()
      val commonEvent1 = CommonEvent(event1)
      val commonEvent2 = CommonEvent(event2)

      externalEventRepo.list(params)(ctx).unsafeRunSync().items shouldBe List(event1, event2)
      externalEventRepo.list(params.page(2))(ctx).unsafeRunSync().items shouldBe List()
      externalEventRepo.list(params.pageSize(5))(ctx).unsafeRunSync().items shouldBe List(event1, event2)
      externalEventRepo.list(params.search(event1.name.value))(ctx).unsafeRunSync().items shouldBe List(event1)
      externalEventRepo.list(params.orderBy("kind"))(ctx).unsafeRunSync().items shouldBe List(event2, event1)
      externalEventRepo.list(params.filters("type" -> event1.kind.value.toLowerCase))(ctx).unsafeRunSync().items shouldBe List(event1)
      externalEventRepo.list(params.filters("video" -> event1.videos.isDefined.toString))(ctx).unsafeRunSync().items shouldBe List(event1)

      externalEventRepo.listCommon(params)(ctx.userAwareCtx).unsafeRunSync().items shouldBe List(commonEvent1, commonEvent2)
      externalEventRepo.listCommon(params.page(2))(ctx.userAwareCtx).unsafeRunSync().items shouldBe List()
      externalEventRepo.listCommon(params.pageSize(5))(ctx.userAwareCtx).unsafeRunSync().items shouldBe List(commonEvent1, commonEvent2)
      externalEventRepo.listCommon(params.search(event1.name.value))(ctx.userAwareCtx).unsafeRunSync().items shouldBe List(commonEvent1)
      externalEventRepo.listCommon(params.orderBy("kind"))(ctx.userAwareCtx).unsafeRunSync().items shouldBe List(commonEvent2, commonEvent1)
      externalEventRepo.listCommon(params.filters("type" -> event1.kind.value.toLowerCase))(ctx.userAwareCtx).unsafeRunSync().items shouldBe List(commonEvent1)
      externalEventRepo.listCommon(params.filters("video" -> event1.videos.isDefined.toString))(ctx.userAwareCtx).unsafeRunSync().items shouldBe List(commonEvent1)
      externalEventRepo.listCommon(params.filters("past" -> event1.start.exists(_.isBefore(nowLDT)).toString))(ctx.userAwareCtx).unsafeRunSync().items shouldBe List(commonEvent1)
    }
    it("should be able to read correctly") {
      val (user, group, ctx) = createOrga().unsafeRunSync()
      val event = externalEventRepo.create(externalEventData1)(ctx).unsafeRunSync()
      val commonEvent = CommonEvent(event)

      externalEventRepo.listAllIds().unsafeRunSync() shouldBe List(event.id)
      externalEventRepo.list(params)(ctx).unsafeRunSync().items shouldBe List(event)
      externalEventRepo.listCommon(params)(ctx.userAwareCtx).unsafeRunSync().items shouldBe List(commonEvent)
      externalEventRepo.find(event.id).unsafeRunSync() shouldBe Some(event)
      externalEventRepo.listTags().unsafeRunSync() shouldBe event.tags
      externalEventRepo.listLogos().unsafeRunSync() shouldBe event.logo.toList
    }
    it("should check queries") {
      check(ExternalEventRepoSql.insert(externalEvent), s"INSERT INTO ${table.stripSuffix(" ee")} (${mapFields(fieldsInsert, _.stripPrefix("ee."))}) VALUES (${mapFields(fieldsInsert, _ => "?")})")
      check(ExternalEventRepoSql.update(externalEvent.id)(externalEvent.data, user.id, now), s"UPDATE $table SET name=?, kind=?, logo=?, description=?, start=?, finish=?, location=?, location_id=?, location_lat=?, location_lng=?, location_locality=?, location_country=?, url=?, tickets_url=?, videos_url=?, twitter_account=?, twitter_hashtag=?, tags=?, updated_at=?, updated_by=? WHERE ee.id=?")
      check(ExternalEventRepoSql.selectOne(externalEvent.id), s"SELECT $fields FROM $table WHERE ee.id=? $orderBy LIMIT 1")
      check(ExternalEventRepoSql.selectAllIds(), s"SELECT ee.id FROM $table $orderBy")
      check(ExternalEventRepoSql.selectPage(params), s"SELECT $fields FROM $table $orderBy LIMIT 20 OFFSET 0")
      unsafeCheck(ExternalEventRepoSql.selectPageCommon(params), s"SELECT $commonFields FROM $commonTable $commonOrderBy LIMIT 20 OFFSET 0")
      check(ExternalEventRepoSql.selectTags(), s"SELECT ee.tags FROM $table $orderBy")
      check(ExternalEventRepoSql.selectLogos(), s"SELECT ee.logo FROM $table WHERE ee.logo IS NOT NULL $orderBy")
    }
  }
}

object ExternalEventRepoSqlSpec {
  val table = "external_events ee"
  val fieldsInsert: String = mapFields("id, name, kind, logo, description, start, finish, location, location_id, location_lat, location_lng, location_locality, location_country, url, tickets_url, videos_url, twitter_account, twitter_hashtag, tags, created_at, created_by, updated_at, updated_by", "ee." + _)
  val fields: String = fieldsInsert.split(", ").filterNot(_.startsWith("ee.location_")).mkString(", ")
  val orderBy = "ORDER BY ee.start IS NULL, ee.start DESC, ee.name IS NULL, ee.name"

  val commonTable: String = "(" +
    "(SELECT e.name, e.kind, e.start, v.address as location, g.social_twitter as twitter_account, null as twitter_hashtag, e.tags, null as ext_id, null as ext_logo, null as ext_description, null as ext_url, null as ext_tickets, null as ext_videos, e.id as int_id, e.slug as int_slug, e.description as int_description, g.id as int_group_id, g.slug as int_group_slug, g.name as int_group_name, g.logo as int_group_logo, c.id as int_cfp_id, c.slug as int_cfp_slug, c.name as int_cfp_name, v.id as int_venue_id, pa.name as int_venue_name, pa.logo as int_venue_logo, e.created_at, e.created_by, e.updated_at, e.updated_by FROM events e INNER JOIN groups g ON e.group_id=g.id LEFT OUTER JOIN cfps c ON e.cfp_id=c.id LEFT OUTER JOIN venues v ON e.venue=v.id LEFT OUTER JOIN partners pa ON v.partner_id=pa.id WHERE e.published IS NOT NULL) UNION " +
    "(SELECT ee.name, ee.kind, ee.start, ee.location, ee.twitter_account, ee.twitter_hashtag, ee.tags, ee.id as ext_id, ee.logo as ext_logo, ee.description as ext_description, ee.url as ext_url, ee.tickets_url as ext_tickets, ee.videos_url as ext_videos, null as int_id, null as int_slug, null as int_description, null as int_group_id, null as int_group_slug, null as int_group_name, null as int_group_logo, null as int_cfp_id, null as int_cfp_slug, null as int_cfp_name, null as int_venue_id, null as int_venue_name, null as int_venue_logo, ee.created_at, ee.created_by, ee.updated_at, ee.updated_by FROM external_events ee)) e"
  val commonFields: String = mapFields("name, kind, start, location, twitter_account, twitter_hashtag, tags, ext_id, ext_logo, ext_description, ext_url, ext_tickets, ext_videos, int_id, int_slug, int_description, int_group_id, int_group_slug, int_group_name, int_group_logo, int_cfp_id, int_cfp_slug, int_cfp_name, int_venue_id, int_venue_name, int_venue_logo, created_at, created_by, updated_at, updated_by", "e." + _)
  val commonOrderBy = "ORDER BY e.start IS NULL, e.start DESC, e.created_at IS NULL, e.created_at DESC"
}
