package fr.gospeak.infra.services.storage.sql

import fr.gospeak.core.domain.Talk
import fr.gospeak.infra.services.storage.sql.TalkRepoSqlSpec._
import fr.gospeak.infra.services.storage.sql.testingutils.RepoSpec

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
        q.sql shouldBe s"INSERT INTO $table ($fields) VALUES (${mapFields(fields, _ => "?")})"
        check(q)
      }
      it("should build update") {
        val q = TalkRepoSql.update(talk.slug)(talkData1, user.id, now)
        q.sql shouldBe s"UPDATE $table SET slug=?, title=?, duration=?, description=?, slides=?, video=?, tags=?, updated=?, updated_by=? WHERE speakers LIKE ? AND slug=?"
        check(q)
      }
      it("should build updateStatus") {
        val q = TalkRepoSql.updateStatus(talk.slug)(Talk.Status.Public, user.id)
        q.sql shouldBe s"UPDATE $table SET status=? WHERE speakers LIKE ? AND slug=?"
        check(q)
      }
      it("should build updateSlides") {
        val q = TalkRepoSql.updateSlides(talk.slug)(slides, user.id, now)
        q.sql shouldBe s"UPDATE $table SET slides=?, updated=?, updated_by=? WHERE speakers LIKE ? AND slug=?"
        check(q)
      }
      it("should build updateVideo") {
        val q = TalkRepoSql.updateVideo(talk.slug)(video, user.id, now)
        q.sql shouldBe s"UPDATE $table SET video=?, updated=?, updated_by=? WHERE speakers LIKE ? AND slug=?"
        check(q)
      }
      it("should build updateSpeakers") {
        val q = TalkRepoSql.updateSpeakers(talk.slug)(talk.speakers, user.id, now)
        q.sql shouldBe s"UPDATE $table SET speakers=?, updated=?, updated_by=? WHERE speakers LIKE ? AND slug=?"
        check(q)
      }
      it("should build selectOne by id") {
        val q = TalkRepoSql.selectOne(talk.id)
        q.sql shouldBe s"SELECT $fields FROM $table WHERE id=?"
        check(q)
      }
      it("should build selectOne by slug") {
        val q = TalkRepoSql.selectOne(talk.slug)
        q.sql shouldBe s"SELECT $fields FROM $table WHERE slug=?"
        check(q)
      }
      it("should build selectOne by user and slug") {
        val q = TalkRepoSql.selectOne(user.id, talk.slug)
        q.sql shouldBe s"SELECT $fields FROM $table WHERE speakers LIKE ? AND slug=?"
        check(q)
      }
      it("should build selectPage for user") {
        val (s, c) = TalkRepoSql.selectPage(user.id, params)
        s.sql shouldBe s"SELECT $fields FROM $table WHERE speakers LIKE ? ORDER BY title IS NULL, title OFFSET 0 LIMIT 20"
        c.sql shouldBe s"SELECT count(*) FROM $table WHERE speakers LIKE ? "
        check(s)
        check(c)
      }
      it("should build selectPage for user and status") {
        val (s, c) = TalkRepoSql.selectPage(user.id, talk.status, params)
        s.sql shouldBe s"SELECT $fields FROM $table WHERE speakers LIKE ? AND status=? ORDER BY title IS NULL, title OFFSET 0 LIMIT 20"
        c.sql shouldBe s"SELECT count(*) FROM $table WHERE speakers LIKE ? AND status=? "
        check(s)
        check(c)
      }
      it("should build selectPage for user, cfp and status") {
        val (s, c) = TalkRepoSql.selectPage(user.id, cfp.id, Talk.Status.active, params)
        s.sql shouldBe s"SELECT $fields FROM $table WHERE speakers LIKE ? AND id NOT IN (SELECT talk_id FROM proposals WHERE cfp_id=?) AND status IN (?, ?, ?, ?)  ORDER BY title IS NULL, title OFFSET 0 LIMIT 20"
        c.sql shouldBe s"SELECT count(*) FROM $table WHERE speakers LIKE ? AND id NOT IN (SELECT talk_id FROM proposals WHERE cfp_id=?) AND status IN (?, ?, ?, ?)  "
        check(s)
        check(c)
      }
    }
  }
}

object TalkRepoSqlSpec {
  val table = "talks"
  val fields = "id, slug, status, title, duration, description, speakers, slides, video, tags, created, created_by, updated, updated_by"
}
