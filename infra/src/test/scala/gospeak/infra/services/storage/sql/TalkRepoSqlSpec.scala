package gospeak.infra.services.storage.sql

import cats.data.NonEmptyList
import gospeak.core.domain.{Talk, User}
import gospeak.infra.services.storage.sql.ProposalRepoSqlSpec.{orderBy => proposalOrderBy, table => proposalTable}
import gospeak.infra.services.storage.sql.TalkRepoSql._
import gospeak.infra.services.storage.sql.TalkRepoSqlSpec._
import gospeak.infra.services.storage.sql.testingutils.RepoSpec
import gospeak.infra.services.storage.sql.testingutils.RepoSpec.mapFields
import gospeak.libs.scala.Extensions._

class TalkRepoSqlSpec extends RepoSpec {
  describe("TalkRepoSql") {
    it("should handle crud operations") {
      val (user, ctx) = createUser().unsafeRunSync()
      talkRepo.list(params)(ctx).unsafeRunSync().items shouldBe List()
      val talk = talkRepo.create(talkData1)(ctx).unsafeRunSync()
      talk.data shouldBe talkData1
      talkRepo.list(params)(ctx).unsafeRunSync().items shouldBe List(talk)

      talkRepo.edit(talk.slug, talkData2)(ctx).unsafeRunSync()
      talkRepo.list(params)(ctx).unsafeRunSync().items.map(_.data) shouldBe List(talkData2)
      // no delete...
    }
    it("should perform specific updates") {
      val (user, ctx) = createUser().unsafeRunSync()
      val (user2, ctx2) = createUser(credentials2, userData2).unsafeRunSync()
      val talk = talkRepo.create(talkData1)(ctx).unsafeRunSync()

      val t1 = talk.copy(status = Talk.Status.Public)
      talkRepo.editStatus(talk.slug, t1.status)(ctx).unsafeRunSync()
      talkRepo.list(params)(ctx).unsafeRunSync().items shouldBe List(t1)

      val t2 = t1.copy(slides = Some(urlSlides))
      talkRepo.editSlides(talk.slug, urlSlides)(ctx).unsafeRunSync()
      talkRepo.list(params)(ctx).unsafeRunSync().items shouldBe List(t2)

      val t3 = t2.copy(video = Some(urlVideo))
      talkRepo.editVideo(talk.slug, urlVideo)(ctx).unsafeRunSync()
      talkRepo.list(params)(ctx).unsafeRunSync().items shouldBe List(t3)

      val t4 = t3.copy(speakers = NonEmptyList.of(user.id, user2.id))
      talkRepo.addSpeaker(talk.id, user.id)(ctx2).unsafeRunSync()
      talkRepo.list(params)(ctx).unsafeRunSync().items shouldBe List(t4)
      an[Exception] should be thrownBy talkRepo.addSpeaker(talk.id, user.id)(ctx2).unsafeRunSync()

      an[Exception] should be thrownBy talkRepo.removeSpeaker(talk.slug, user.id)(ctx).unsafeRunSync()
      an[Exception] should be thrownBy talkRepo.removeSpeaker(talk.slug, User.Id.generate())(ctx).unsafeRunSync()
      talkRepo.removeSpeaker(talk.slug, user2.id)(ctx).unsafeRunSync()
      talkRepo.list(params)(ctx).unsafeRunSync().items shouldBe List(t3)
    }
    it("should only retrieve owned talks") {
      val (user, ctx) = createUser().unsafeRunSync()
      val (user2, ctx2) = createUser(credentials2, userData2).unsafeRunSync()
      val talk = talkRepo.create(talkData1)(ctx).unsafeRunSync()
      talkRepo.list(params)(ctx).unsafeRunSync().items shouldBe List(talk)
      talkRepo.list(params)(ctx2).unsafeRunSync().items shouldBe List()
    }
    it("should fail on duplicate slug") {
      val (user, ctx) = createUser().unsafeRunSync()
      val (user2, ctx2) = createUser(credentials2, userData2).unsafeRunSync()
      talkRepo.create(talkData1)(ctx).unsafeRunSync()
      an[Exception] should be thrownBy talkRepo.create(talkData1)(ctx2).unsafeRunSync()
    }
    it("should fail to change slug for an existing one") {
      val (user, ctx) = createUser().unsafeRunSync()
      talkRepo.create(talkData1)(ctx).unsafeRunSync()
      talkRepo.create(talkData2)(ctx).unsafeRunSync()
      an[Exception] should be thrownBy talkRepo.edit(talkData1.slug, talkData1.copy(slug = talkData2.slug))(ctx).unsafeRunSync()
    }
    it("should select a page") {
      val (user, ctx) = createUser().unsafeRunSync()
      val (user2, ctx2) = createUser(credentials2, userData2).unsafeRunSync()
      val talk1 = talkRepo.create(talkData1.copy(slug = Talk.Slug.from("dddddd").get, title = Talk.Title("aaaaaa")))(ctx).unsafeRunSync()
      val talk2 = talkRepo.create(talkData2.copy(slug = Talk.Slug.from("bbbbbb").get, title = Talk.Title("cccccc")))(ctx).unsafeRunSync()
      val talk3 = talkRepo.create(talkData3)(ctx2).unsafeRunSync()

      talkRepo.list(params)(ctx).unsafeRunSync().items shouldBe List(talk1, talk2)
      talkRepo.list(params.page(2))(ctx).unsafeRunSync().items shouldBe List()
      talkRepo.list(params.pageSize(5))(ctx).unsafeRunSync().items shouldBe List(talk1, talk2)
      talkRepo.list(params.search(talk1.title.value))(ctx).unsafeRunSync().items shouldBe List(talk1)
      talkRepo.list(params.orderBy("slug"))(ctx).unsafeRunSync().items shouldBe List(talk2, talk1)
    }
    it("should be able to read correctly") {
      val (user, ctx) = createUser().unsafeRunSync()
      val talk = talkRepo.create(talkData1)(ctx).unsafeRunSync()
      talkRepo.editStatus(talk.slug, Talk.Status.Public)(ctx).unsafeRunSync()

      talkRepo.find(talk.id).unsafeRunSync() shouldBe Some(talk)
      talkRepo.find(talk.slug)(ctx).unsafeRunSync() shouldBe Some(talk)
      talkRepo.findPublic(talk.slug, user.id).unsafeRunSync() shouldBe Some(talk)
      talkRepo.list(params)(ctx).unsafeRunSync().items shouldBe List(talk)
      talkRepo.listAll(user.id, Talk.Status.Public).unsafeRunSync() shouldBe List(talk)
      talkRepo.listAllPublicSlugs().unsafeRunSync() shouldBe List(talk.slug -> talk.speakers)
      talkRepo.listCurrent(params)(ctx).unsafeRunSync().items shouldBe List(talk)
      talkRepo.listCurrent(cfp.id, params)(ctx).unsafeRunSync().items shouldBe List(talk)
      talkRepo.exists(talk.slug).unsafeRunSync() shouldBe true
      talkRepo.listTags().unsafeRunSync() shouldBe talk.tags.distinct
    }
    it("should check queries") {
      check(insert(talk), s"INSERT INTO ${table.stripSuffix(" t")} (${mapFields(fields, _.stripPrefix("t."))}) VALUES (${mapFields(fields, _ => "?")})")
      check(update(talk.slug)(talkData1, user.id, now), s"UPDATE $table SET slug=?, title=?, duration=?, description=?, message=?, slides=?, video=?, tags=?, updated_at=?, updated_by=? WHERE t.speakers LIKE ? AND t.slug=?")
      check(updateStatus(talk.slug)(Talk.Status.Public, user.id), s"UPDATE $table SET status=? WHERE t.speakers LIKE ? AND t.slug=?")
      check(updateSlides(talk.slug)(urlSlides, user.id, now), s"UPDATE $table SET slides=?, updated_at=?, updated_by=? WHERE t.speakers LIKE ? AND t.slug=?")
      check(updateVideo(talk.slug)(urlVideo, user.id, now), s"UPDATE $table SET video=?, updated_at=?, updated_by=? WHERE t.speakers LIKE ? AND t.slug=?")
      check(updateSpeakers(talk.slug)(talk.speakers, user.id, now), s"UPDATE $table SET speakers=?, updated_at=?, updated_by=? WHERE t.speakers LIKE ? AND t.slug=?")
      check(selectOne(talk.id), s"SELECT $fields FROM $table WHERE t.id=? $orderBy")
      check(selectOne(talk.slug), s"SELECT $fields FROM $table WHERE t.slug=? $orderBy")
      check(selectOne(user.id, talk.slug), s"SELECT $fields FROM $table WHERE t.speakers LIKE ? AND t.slug=? $orderBy")
      check(selectOne(user.id, talk.slug, talk.status), s"SELECT $fields FROM $table WHERE t.speakers LIKE ? AND t.slug=? AND t.status=? $orderBy LIMIT 1")
      check(selectPage(params), s"SELECT $fields FROM $table WHERE t.speakers LIKE ? $orderBy LIMIT 20 OFFSET 0")
      check(selectAll(user.id, talk.status), s"SELECT $fields FROM $table WHERE t.speakers LIKE ? AND t.status=? $orderBy")
      check(selectAllPublicSlugs(), s"SELECT t.slug, t.speakers FROM $table WHERE t.status=? $orderBy")
      check(selectPage(Talk.Status.current, params), s"SELECT $fields FROM $table WHERE t.speakers LIKE ? AND t.status IN (?, ?) $orderBy LIMIT 20 OFFSET 0")
      check(selectPage(cfp.id, Talk.Status.current, params), s"SELECT $fields FROM $table WHERE t.speakers LIKE ? AND t.id NOT IN (SELECT p.talk_id FROM $proposalTable WHERE p.cfp_id=? $proposalOrderBy) AND t.status IN (?, ?) $orderBy LIMIT 20 OFFSET 0")
      check(selectTags(), s"SELECT t.tags FROM $table $orderBy")
    }
  }
}

object TalkRepoSqlSpec {
  val table = "talks t"
  val fields: String = mapFields("id, slug, status, title, duration, description, message, speakers, slides, video, tags, created_at, created_by, updated_at, updated_by", "t." + _)
  val orderBy = "ORDER BY t.status = 'Archived' IS NULL, t.status = 'Archived', t.title IS NULL, t.title"
}
