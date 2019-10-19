package fr.gospeak.infra.services.storage.sql

import fr.gospeak.core.domain.Talk
import fr.gospeak.infra.services.storage.sql.ProposalRepoSqlSpec.{table => proposalTable}
import fr.gospeak.infra.services.storage.sql.TalkRepoSqlSpec._
import fr.gospeak.infra.services.storage.sql.testingutils.RepoSpec
import fr.gospeak.infra.services.storage.sql.testingutils.RepoSpec.mapFields

class TalkRepoSqlSpec extends RepoSpec {
  describe("TalkRepoSql") {
    it("should create and retrieve") {
      val user = userRepo.create(userData1, now).unsafeRunSync()
      talkRepo.list(user.id, params).unsafeRunSync().items shouldBe Seq()
      talkRepo.find(user.id, talkData1.slug).unsafeRunSync() shouldBe None
      val talk = talkRepo.create(talkData1, user.id, now).unsafeRunSync()
      talkRepo.list(user.id, params).unsafeRunSync().items shouldBe Seq(talk)
      talkRepo.find(user.id, talkData1.slug).unsafeRunSync() shouldBe Some(talk)
    }
    it("should not retrieve not owned talks") {
      val user = userRepo.create(userData1, now).unsafeRunSync()
      val user2 = userRepo.create(userData2, now).unsafeRunSync()
      val talk = talkRepo.create(talkData1, user2.id, now).unsafeRunSync()
      talkRepo.list(user.id, params).unsafeRunSync().items shouldBe Seq()
      talkRepo.find(user.id, talkData1.slug).unsafeRunSync() shouldBe None
    }
    it("should fail on duplicate slug") {
      val user = userRepo.create(userData1, now).unsafeRunSync()
      val user2 = userRepo.create(userData2, now).unsafeRunSync()
      talkRepo.create(talkData1, user.id, now).unsafeRunSync()
      an[Exception] should be thrownBy talkRepo.create(talkData1, user2.id, now).unsafeRunSync()
    }
    it("should update talk data") {
      val user = userRepo.create(userData1, now).unsafeRunSync()
      talkRepo.create(talkData1, user.id, now).unsafeRunSync()
      talkRepo.find(user.id, talkData1.slug).unsafeRunSync().map(_.data) shouldBe Some(talkData1)
      talkRepo.edit(talkData1.slug)(talkData2, user.id, now).unsafeRunSync()
      talkRepo.find(user.id, talkData1.slug).unsafeRunSync() shouldBe None
      talkRepo.find(user.id, talkData2.slug).unsafeRunSync().map(_.data) shouldBe Some(talkData2)
    }
    it("should fail to change slug for an existing one") {
      val user = userRepo.create(userData1, now).unsafeRunSync()
      talkRepo.create(talkData1, user.id, now).unsafeRunSync()
      talkRepo.create(talkData2, user.id, now).unsafeRunSync()
      an[Exception] should be thrownBy talkRepo.edit(talkData1.slug)(talkData1.copy(slug = talkData2.slug), user.id, now).unsafeRunSync()
    }
    it("should update the status") {
      val user = userRepo.create(userData1, now).unsafeRunSync()
      talkRepo.create(talkData1, user.id, now).unsafeRunSync()
      talkRepo.find(user.id, talkData1.slug).unsafeRunSync().map(_.status) shouldBe Some(Talk.Status.Draft)
      talkRepo.editStatus(talkData1.slug)(Talk.Status.Public, user.id).unsafeRunSync()
      talkRepo.find(user.id, talkData1.slug).unsafeRunSync().map(_.status) shouldBe Some(Talk.Status.Public)
    }
    describe("Queries") {
      it("should build insert") {
        val q = TalkRepoSql.insert(talk)
        check(q, s"INSERT INTO ${table.stripSuffix(" t")} (${mapFields(fields, _.stripPrefix("t."))}) VALUES (${mapFields(fields, _ => "?")})")
      }
      it("should build update") {
        val q = TalkRepoSql.update(talk.slug)(talkData1, user.id, now)
        check(q, s"UPDATE $table SET t.slug=?, t.title=?, t.duration=?, t.description=?, t.slides=?, t.video=?, t.tags=?, t.updated=?, t.updated_by=? WHERE t.speakers LIKE ? AND t.slug=?")
      }
      it("should build updateStatus") {
        val q = TalkRepoSql.updateStatus(talk.slug)(Talk.Status.Public, user.id)
        check(q, s"UPDATE $table SET t.status=? WHERE t.speakers LIKE ? AND t.slug=?")
      }
      it("should build updateSlides") {
        val q = TalkRepoSql.updateSlides(talk.slug)(slides, user.id, now)
        check(q, s"UPDATE $table SET t.slides=?, t.updated=?, t.updated_by=? WHERE t.speakers LIKE ? AND t.slug=?")
      }
      it("should build updateVideo") {
        val q = TalkRepoSql.updateVideo(talk.slug)(video, user.id, now)
        check(q, s"UPDATE $table SET t.video=?, t.updated=?, t.updated_by=? WHERE t.speakers LIKE ? AND t.slug=?")
      }
      it("should build updateSpeakers") {
        val q = TalkRepoSql.updateSpeakers(talk.slug)(talk.speakers, user.id, now)
        check(q, s"UPDATE $table SET t.speakers=?, t.updated=?, t.updated_by=? WHERE t.speakers LIKE ? AND t.slug=?")
      }
      it("should build selectOne by id") {
        val q = TalkRepoSql.selectOne(talk.id)
        check(q, s"SELECT $fields FROM $table WHERE t.id=?")
      }
      it("should build selectOne by slug") {
        val q = TalkRepoSql.selectOne(talk.slug)
        check(q, s"SELECT $fields FROM $table WHERE t.slug=?")
      }
      it("should build selectOne by user and slug") {
        val q = TalkRepoSql.selectOne(user.id, talk.slug)
        check(q, s"SELECT $fields FROM $table WHERE t.speakers LIKE ? AND t.slug=?")
      }
      it("should build selectPage for user") {
        val q = TalkRepoSql.selectPage(user.id, params)
        check(q, s"SELECT $fields FROM $table WHERE t.speakers LIKE ? ORDER BY t.title IS NULL, t.title OFFSET 0 LIMIT 20")
      }
      it("should build selectPage for user and status") {
        val q = TalkRepoSql.selectPage(user.id, talk.status, params)
        check(q, s"SELECT $fields FROM $table WHERE t.speakers LIKE ? AND t.status=? ORDER BY t.title IS NULL, t.title OFFSET 0 LIMIT 20")
      }
      it("should build selectPage for user, cfp and status") {
        val q = TalkRepoSql.selectPage(user.id, cfp.id, Talk.Status.active, params)
        check(q, s"SELECT $fields FROM $table WHERE t.speakers LIKE ? AND t.id NOT IN (SELECT p.talk_id FROM $proposalTable WHERE p.cfp_id=?) AND t.status IN (?, ?, ?, ?)  ORDER BY t.title IS NULL, t.title OFFSET 0 LIMIT 20")
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
  val fields: String = mapFields("id, slug, status, title, duration, description, speakers, slides, video, tags, created, created_by, updated, updated_by", "t." + _)
}
