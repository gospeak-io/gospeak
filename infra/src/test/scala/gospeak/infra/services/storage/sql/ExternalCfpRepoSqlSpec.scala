package gospeak.infra.services.storage.sql

import gospeak.core.domain.{CommonCfp, ExternalCfp}
import gospeak.infra.services.storage.sql.ExternalCfpRepoSql._
import gospeak.infra.services.storage.sql.ExternalCfpRepoSqlSpec._
import gospeak.infra.services.storage.sql.ExternalEventRepoSqlSpec.{fields => eventFields, table => eventTable}
import gospeak.infra.services.storage.sql.testingutils.RepoSpec
import gospeak.infra.services.storage.sql.testingutils.RepoSpec.mapFields

class ExternalCfpRepoSqlSpec extends RepoSpec {
  private val dupParams = ExternalCfp.DuplicateParams(Some("a"), Some("a"), Some(nowLDT), Some("a"), Some(nowLDT), Some("a"), Some("a"))

  describe("ExternalCfpRepoSql") {
    it("should handle crud operations") {
      val (user, group, ctx) = createOrga().unsafeRunSync()
      val event = externalEventRepo.create(externalEventData1)(ctx).unsafeRunSync()
      externalCfpRepo.listAll(event.id).unsafeRunSync() shouldBe List()
      val cfp = externalCfpRepo.create(event.id, externalCfpData1)(ctx).unsafeRunSync()
      cfp.data shouldBe externalCfpData1
      externalCfpRepo.listAll(event.id).unsafeRunSync() shouldBe List(cfp)

      externalCfpRepo.edit(cfp.id)(externalCfpData2)(ctx).unsafeRunSync()
      externalCfpRepo.listAll(event.id).unsafeRunSync().map(_.data) shouldBe List(externalCfpData2)
      // no delete...
    }
    it("should select a page") {
      val (user, group, ctx) = createOrga().unsafeRunSync()
      val event1 = externalEventRepo.create(externalEventData1)(ctx).unsafeRunSync()
      val event2 = externalEventRepo.create(externalEventData2)(ctx).unsafeRunSync()
      val cfp1 = externalCfpRepo.create(event1.id, externalCfpData1.copy(close = Some(nowLDT.minusDays(1))))(ctx).unsafeRunSync()
      val cfp2 = externalCfpRepo.create(event2.id, externalCfpData2.copy(close = Some(nowLDT.plusDays(1))))(ctx).unsafeRunSync()
      val commonCfp1 = CommonCfp(ExternalCfp.Full(cfp1, event1))
      val commonCfp2 = CommonCfp(ExternalCfp.Full(cfp2, event2))
      externalCfpRepo.listIncoming(params)(ctx.userAwareCtx).unsafeRunSync().items shouldBe List(commonCfp2)
      externalCfpRepo.listIncoming(params.page(2))(ctx.userAwareCtx).unsafeRunSync().items shouldBe List()
      externalCfpRepo.listIncoming(params.pageSize(5))(ctx.userAwareCtx).unsafeRunSync().items shouldBe List(commonCfp2)
      externalCfpRepo.listIncoming(params.search(event1.name.value))(ctx.userAwareCtx).unsafeRunSync().items shouldBe List()
      externalCfpRepo.listIncoming(params.search(event2.name.value))(ctx.userAwareCtx).unsafeRunSync().items shouldBe List(commonCfp2)
      externalCfpRepo.listIncoming(params.orderBy("name"))(ctx.userAwareCtx).unsafeRunSync().items shouldBe List(commonCfp2)
    }
    it("should be able to read correctly") {
      val (user, group, ctx) = createOrga().unsafeRunSync()
      val event = externalEventRepo.create(externalEventData1)(ctx).unsafeRunSync()
      val extCfp = externalCfpRepo.create(event.id, externalCfpData1)(ctx).unsafeRunSync()
      val cfp = cfpRepo.create(cfpData1)(ctx).unsafeRunSync()
      val extCfpFull = ExternalCfp.Full(extCfp, event)
      val commonCfp = CommonCfp(group, cfp)
      val commonExtCfp = CommonCfp(extCfpFull)

      externalCfpRepo.listAllIds().unsafeRunSync() shouldBe List(extCfp.id)
      externalCfpRepo.listAll(event.id).unsafeRunSync() shouldBe List(extCfp)
      externalCfpRepo.listDuplicatesFull(dupParams.copy(cfpUrl = Some(extCfp.url.value))).unsafeRunSync() shouldBe List(extCfpFull)
      externalCfpRepo.findFull(extCfp.id).unsafeRunSync() shouldBe Some(extCfpFull)
      externalCfpRepo.findCommon(cfp.slug).unsafeRunSync() shouldBe Some(commonCfp)
      externalCfpRepo.findCommon(extCfp.id).unsafeRunSync() shouldBe Some(commonExtCfp)
    }
    it("should check queries") {
      check(insert(externalCfp), s"INSERT INTO ${table.stripSuffix(" ec")} (${mapFields(fields, _.stripPrefix("ec."))}) VALUES (${mapFields(fields, _ => "?")})")
      check(update(externalCfp.id)(externalCfp.data, user.id, now), s"UPDATE $table SET description=?, begin=?, close=?, url=?, updated_at=?, updated_by=? WHERE ec.id=?")
      check(selectAllIds(), s"SELECT ec.id FROM $table $orderBy")
      check(selectAll(externalEvent.id), s"SELECT $fields FROM $table WHERE ec.event_id=? $orderBy")
      check(selectOneFull(externalCfp.id), s"SELECT $fieldsFull FROM $tableFull WHERE ec.id=? $orderBy LIMIT 1")
      unsafeCheck(selectOneCommon(cfp.slug), s"SELECT $commonFields FROM $commonTable WHERE c.int_slug=? $commonOrderBy LIMIT 1")
      unsafeCheck(selectOneCommon(externalCfp.id), s"SELECT $commonFields FROM $commonTable WHERE c.ext_id=? $commonOrderBy LIMIT 1")
      unsafeCheck(selectCommonPageIncoming(params), s"SELECT $commonFields FROM $commonTable WHERE c.close IS NULL OR c.close >= ? ORDER BY c.close IS NULL, c.close, c.name IS NULL, c.name LIMIT 20 OFFSET 0")
      check(selectDuplicatesFull(dupParams), s"SELECT $fieldsFull FROM $tableFull WHERE ec.url LIKE ? OR ec.close=? $orderBy")
      check(selectDuplicatesFull(ExternalCfp.DuplicateParams.defaults), s"SELECT $fieldsFull FROM $tableFull WHERE ec.id=? $orderBy")
    }
  }
}

object ExternalCfpRepoSqlSpec {
  val table = "external_cfps ec"
  val fields: String = mapFields("id, event_id, description, begin, close, url, created_at, created_by, updated_at, updated_by", "ec." + _)
  val orderBy = "ORDER BY ec.close IS NULL, ec.close"

  val tableFull = s"$table INNER JOIN $eventTable ON ec.event_id=ee.id"
  val fieldsFull = s"$fields, $eventFields"

  val commonTable: String = "(" +
    "(SELECT c.name, g.logo, c.begin, c.close, g.location, c.description, c.tags, null as ext_id, null as ext_url, null as ext_event_start, null as ext_event_finish, null as ext_event_url, null as ext_tickets_url, null as ext_videos_url, null as twitter_account, null as twitter_hashtag, c.slug as int_slug, g.id as group_id, g.slug as group_slug FROM cfps c INNER JOIN groups g ON c.group_id=g.id) UNION " +
    "(SELECT ee.name, ee.logo, ec.begin, ec.close, ee.location, ec.description, ee.tags, ec.id as ext_id, ec.url as ext_url, ee.start as ext_event_start, ee.finish as ext_event_finish, ee.url as ext_event_url, ee.tickets_url as ext_tickets_url, ee.videos_url as ext_videos_url, ee.twitter_account, ee.twitter_hashtag, null as int_slug, null as group_id, null as group_slug FROM external_cfps ec INNER JOIN external_events ee ON ec.event_id=ee.id)) c"
  val commonFields: String = mapFields("name, logo, begin, close, location, description, tags, ext_id, ext_url, ext_event_start, ext_event_finish, ext_event_url, ext_tickets_url, ext_videos_url, twitter_account, twitter_hashtag, int_slug, group_id, group_slug", "c." + _)
  val commonOrderBy = "ORDER BY c.close IS NULL, c.close, c.name IS NULL, c.name"
}
