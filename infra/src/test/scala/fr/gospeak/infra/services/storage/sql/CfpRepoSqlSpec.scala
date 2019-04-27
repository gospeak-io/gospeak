package fr.gospeak.infra.services.storage.sql

import fr.gospeak.core.domain.{Group, Talk}
import CfpRepoSql._
import cats.data.NonEmptyList
import fr.gospeak.infra.services.storage.sql.testingutils.RepoSpec

class CfpRepoSqlSpec extends RepoSpec {
  private val fields = "id, group_id, slug, name, start, end, description, created, created_by, updated, updated_by"

  describe("CfpRepoSql") {
    it("should create and retrieve a cfp for a group") {
      val (user, group) = createUserAndGroup().unsafeRunSync()
      val talkId = Talk.Id.generate()
      cfpRepo.find(cfpData1.slug).unsafeRunSync() shouldBe None
      cfpRepo.list(group.id, page).unsafeRunSync().items shouldBe Seq()
      cfpRepo.availableFor(talkId, page).unsafeRunSync().items shouldBe Seq()
      val cfp = cfpRepo.create(group.id, cfpData1, user.id, now).unsafeRunSync()
      cfpRepo.find(cfp.id).unsafeRunSync() shouldBe Some(cfp)
      cfpRepo.find(cfpData1.slug).unsafeRunSync() shouldBe Some(cfp)
      cfpRepo.list(group.id, page).unsafeRunSync().items shouldBe Seq(cfp)
      cfpRepo.availableFor(talkId, page).unsafeRunSync().items shouldBe Seq(cfp)
    }
    it("should fail to create a cfp when the group does not exists") {
      val user = userRepo.create(userData1, now).unsafeRunSync()
      an[Exception] should be thrownBy cfpRepo.create(Group.Id.generate(), cfpData1, user.id, now).unsafeRunSync()
    }
    it("should fail to create two cfp for a group") {
      val (user, group) = createUserAndGroup().unsafeRunSync()
      cfpRepo.create(group.id, cfpData1, user.id, now).unsafeRunSync()
      an[Exception] should be thrownBy cfpRepo.create(group.id, cfpData1, user.id, now).unsafeRunSync()
    }
    it("should fail on duplicate slug") {
      val (user, group1) = createUserAndGroup().unsafeRunSync()
      val group2 = groupRepo.create(groupData2, user.id, now).unsafeRunSync()
      cfpRepo.create(group1.id, cfpData1, user.id, now).unsafeRunSync()
      an[Exception] should be thrownBy cfpRepo.create(group2.id, cfpData1, user.id, now).unsafeRunSync()
    }
    describe("Queries") {
      it("should build insert") {
        val q = insert(cfp)
        q.sql shouldBe s"INSERT INTO cfps ($fields) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)"
        check(q)
      }
      it("should build update") {
        val q = update(group.id, cfp.slug)(cfpData1, user.id, now)
        q.sql shouldBe "UPDATE cfps SET slug=?, name=?, start=?, end=?, description=?, updated=?, updated_by=? WHERE group_id=? AND slug=?"
        check(q)
      }
      it("should build selectOne for cfp id") {
        val q = selectOne(cfp.id)
        q.sql shouldBe s"SELECT $fields FROM cfps WHERE id=?"
        check(q)
      }
      it("should build selectOne for cfp slug") {
        val q = selectOne(cfp.slug)
        q.sql shouldBe s"SELECT $fields FROM cfps WHERE slug=?"
        check(q)
      }
      it("should build selectOne for group id and cfp slug") {
        val q = selectOne(group.id, cfp.slug)
        q.sql shouldBe s"SELECT $fields FROM cfps WHERE group_id=? AND slug=?"
        check(q)
      }
      it("should build selectOne for event id") {
        val q = selectOne(event.id)
        q.sql shouldBe s"SELECT ${fields.split(", ").map("c." + _).mkString(", ")} FROM cfps c INNER JOIN events e ON e.cfp_id=c.id WHERE e.id=?"
        check(q)
      }
      it("should build selectOne for cfp slug id and date") {
        val q = selectOne(cfp.slug, now)
        q.sql shouldBe s"SELECT $fields FROM cfps WHERE (start IS NULL OR start < ?) AND (end IS NULL OR end > ?) AND slug=?"
        check(q)
      }
      it("should build selectPage for a group") {
        val (s, c) = selectPage(group.id, params)
        s.sql shouldBe s"SELECT $fields FROM cfps WHERE group_id=? ORDER BY name IS NULL, name OFFSET 0 LIMIT 20"
        c.sql shouldBe "SELECT count(*) FROM cfps WHERE group_id=? "
        check(s)
        check(c)
      }
      it("should build selectPage for a talk") {
        val (s, c) = selectPage(talk.id, params)
        s.sql shouldBe s"SELECT $fields FROM cfps WHERE id NOT IN (SELECT cfp_id FROM proposals WHERE talk_id=?) ORDER BY name IS NULL, name OFFSET 0 LIMIT 20"
        c.sql shouldBe "SELECT count(*) FROM cfps WHERE id NOT IN (SELECT cfp_id FROM proposals WHERE talk_id=?) "
        check(s)
        check(c)
      }
      it("should build selectPage for a date") {
        val (s, c) = selectPage(now, params)
        s.sql shouldBe s"SELECT $fields FROM cfps WHERE (start IS NULL OR start < ?) AND (end IS NULL OR end > ?) ORDER BY name IS NULL, name OFFSET 0 LIMIT 20"
        c.sql shouldBe "SELECT count(*) FROM cfps WHERE (start IS NULL OR start < ?) AND (end IS NULL OR end > ?) "
        check(s)
        check(c)
      }
      it("should build selectAll for group id") {
        val q = selectAll(group.id)
        q.sql shouldBe s"SELECT $fields FROM cfps WHERE group_id=?"
        check(q)
      }
      it("should build selectAll for cfp ids") {
        val q = selectAll(NonEmptyList.of(cfp.id, cfp.id, cfp.id))
        q.sql shouldBe s"SELECT $fields FROM cfps WHERE id IN (?, ?, ?) "
        check(q)
      }
      it("should build selectAll for group and date") {
        val q = selectAll(group.id, now)
        q.sql shouldBe s"SELECT $fields FROM cfps WHERE (start IS NULL OR start < ?) AND (end IS NULL OR end > ?) AND group_id=?"
        check(q)
      }
    }
  }
}
