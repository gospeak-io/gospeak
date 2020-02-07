package gospeak.infra.services.storage.sql

import gospeak.core.domain.Talk
import gospeak.core.domain.utils.FakeCtx
import gospeak.infra.services.storage.sql.ProposalRepoSqlSpec.{table => proposalTable}
import gospeak.infra.services.storage.sql.TalkRepoSqlSpec._
import gospeak.infra.services.storage.sql.testingutils.RepoSpec
import gospeak.infra.services.storage.sql.testingutils.RepoSpec.mapFields

class TalkRepoSqlSpec extends RepoSpec {
  describe("TalkRepoSql") {
    it("should create and retrieve") {
      val user = userRepo.create(userData1, now, None).unsafeRunSync()
      val ctx = FakeCtx(now, user)
      talkRepo.list(params)(ctx).unsafeRunSync().items shouldBe Seq()
      talkRepo.find(talkData1.slug)(ctx).unsafeRunSync() shouldBe None
      val talk = talkRepo.create(talkData1)(ctx).unsafeRunSync()
      talkRepo.list(params)(ctx).unsafeRunSync().items shouldBe Seq(talk)
      talkRepo.find(talkData1.slug)(ctx).unsafeRunSync() shouldBe Some(talk)
    }
    it("should not retrieve not owned talks") {
      val user = userRepo.create(userData1, now, None).unsafeRunSync()
      val ctx = FakeCtx(now, user)
      val user2 = userRepo.create(userData2, now, None).unsafeRunSync()
      val ctx2 = FakeCtx(now, user2)
      val talk = talkRepo.create(talkData1)(ctx2).unsafeRunSync()
      talkRepo.list(params)(ctx).unsafeRunSync().items shouldBe Seq()
      talkRepo.find(talkData1.slug)(ctx).unsafeRunSync() shouldBe None
    }
    it("should fail on duplicate slug") {
      val user = userRepo.create(userData1, now, None).unsafeRunSync()
      val user2 = userRepo.create(userData2, now, None).unsafeRunSync()
      val ctx = FakeCtx(now, user)
      val ctx2 = FakeCtx(now, user2)
      talkRepo.create(talkData1)(ctx).unsafeRunSync()
      an[Exception] should be thrownBy talkRepo.create(talkData1)(ctx2).unsafeRunSync()
    }
    it("should update talk data") {
      val user = userRepo.create(userData1, now, None).unsafeRunSync()
      val ctx = FakeCtx(now, user)
      talkRepo.create(talkData1)(ctx).unsafeRunSync()
      talkRepo.find(talkData1.slug)(ctx).unsafeRunSync().map(_.data) shouldBe Some(talkData1)
      talkRepo.edit(talkData1.slug, talkData2)(ctx).unsafeRunSync()
      talkRepo.find(talkData1.slug)(ctx).unsafeRunSync() shouldBe None
      talkRepo.find(talkData2.slug)(ctx).unsafeRunSync().map(_.data) shouldBe Some(talkData2)
    }
    it("should fail to change slug for an existing one") {
      val user = userRepo.create(userData1, now, None).unsafeRunSync()
      val ctx = FakeCtx(now, user)
      talkRepo.create(talkData1)(ctx).unsafeRunSync()
      talkRepo.create(talkData2)(ctx).unsafeRunSync()
      an[Exception] should be thrownBy talkRepo.edit(talkData1.slug, talkData1.copy(slug = talkData2.slug))(ctx).unsafeRunSync()
    }
    it("should update the status") {
      val user = userRepo.create(userData1, now, None).unsafeRunSync()
      val ctx = FakeCtx(now, user)
      talkRepo.create(talkData1)(ctx).unsafeRunSync()
      talkRepo.find(talkData1.slug)(ctx).unsafeRunSync().map(_.status) shouldBe Some(Talk.Status.Draft)
      talkRepo.editStatus(talkData1.slug, Talk.Status.Public)(ctx).unsafeRunSync()
      talkRepo.find(talkData1.slug)(ctx).unsafeRunSync().map(_.status) shouldBe Some(Talk.Status.Public)
    }
    describe("Queries") {
      it("should build insert") {
        val q = TalkRepoSql.insert(talk)
        check(q, s"INSERT INTO ${table.stripSuffix(" t")} (${mapFields(fields, _.stripPrefix("t."))}) VALUES (${mapFields(fields, _ => "?")})")
      }
      it("should build update") {
        val q = TalkRepoSql.update(talk.slug)(talkData1, user.id, now)
        check(q, s"UPDATE $table SET slug=?, title=?, duration=?, description=?, message=?, slides=?, video=?, tags=?, updated_at=?, updated_by=? WHERE t.speakers LIKE ? AND t.slug=?")
      }
      it("should build updateStatus") {
        val q = TalkRepoSql.updateStatus(talk.slug)(Talk.Status.Public, user.id)
        check(q, s"UPDATE $table SET status=? WHERE t.speakers LIKE ? AND t.slug=?")
      }
      it("should build updateSlides") {
        val q = TalkRepoSql.updateSlides(talk.slug)(slides, user.id, now)
        check(q, s"UPDATE $table SET slides=?, updated_at=?, updated_by=? WHERE t.speakers LIKE ? AND t.slug=?")
      }
      it("should build updateVideo") {
        val q = TalkRepoSql.updateVideo(talk.slug)(video, user.id, now)
        check(q, s"UPDATE $table SET video=?, updated_at=?, updated_by=? WHERE t.speakers LIKE ? AND t.slug=?")
      }
      it("should build updateSpeakers") {
        val q = TalkRepoSql.updateSpeakers(talk.slug)(talk.speakers, user.id, now)
        check(q, s"UPDATE $table SET speakers=?, updated_at=?, updated_by=? WHERE t.speakers LIKE ? AND t.slug=?")
      }
      it("should build selectOne by id") {
        val q = TalkRepoSql.selectOne(talk.id)
        check(q, s"SELECT $fields FROM $table WHERE t.id=? $orderBy")
      }
      it("should build selectOne by slug") {
        val q = TalkRepoSql.selectOne(talk.slug)
        check(q, s"SELECT $fields FROM $table WHERE t.slug=? $orderBy")
      }
      it("should build selectOne by user and slug") {
        val q = TalkRepoSql.selectOne(user.id, talk.slug)
        check(q, s"SELECT $fields FROM $table WHERE t.speakers LIKE ? AND t.slug=? $orderBy")
      }
      it("should build selectPage for user") {
        val q = TalkRepoSql.selectPage(user.id, params)
        check(q, s"SELECT $fields FROM $table WHERE t.speakers LIKE ? $orderBy LIMIT 20 OFFSET 0")
      }
      it("should build selectAll for user and status") {
        val q = TalkRepoSql.selectAll(user.id, talk.status)
        check(q, s"SELECT $fields FROM $table WHERE t.speakers LIKE ? AND t.status=? $orderBy")
      }
      it("should build selectPage for user and list of status") {
        val q = TalkRepoSql.selectPage(user.id, Talk.Status.current, params)
        check(q, s"SELECT $fields FROM $table WHERE t.speakers LIKE ? AND t.status IN (?, ?, ?, ?)  $orderBy LIMIT 20 OFFSET 0")
      }
      it("should build selectPage for user, cfp and status") {
        val q = TalkRepoSql.selectPage(user.id, cfp.id, Talk.Status.current, params)
        check(q, s"SELECT $fields FROM $table WHERE t.speakers LIKE ? AND t.id NOT IN (SELECT p.talk_id FROM $proposalTable WHERE p.cfp_id=?) AND t.status IN (?, ?, ?, ?)  $orderBy LIMIT 20 OFFSET 0")
      }
      it("should build selectTags") {
        val q = TalkRepoSql.selectTags()
        check(q, s"SELECT t.tags FROM $table")
      }
    }
  }
}

object TalkRepoSqlSpec {
  val table = "talks t"
  val fields: String = mapFields("id, slug, status, title, duration, description, message, speakers, slides, video, tags, created_at, created_by, updated_at, updated_by", "t." + _)
  val orderBy = "ORDER BY t.status = 'Archived' IS NULL, t.status = 'Archived', t.title IS NULL, t.title"
}
