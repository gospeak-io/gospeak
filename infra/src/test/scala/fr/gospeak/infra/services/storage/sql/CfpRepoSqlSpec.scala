package fr.gospeak.infra.services.storage.sql

import fr.gospeak.core.domain.{Group, Talk}
import CfpRepoSql._
import fr.gospeak.infra.services.storage.sql.testingutils.RepoSpec

class CfpRepoSqlSpec extends RepoSpec {
  describe("CfpRepoSql") {
    it("should create and retrieve a cfp for a group") {
      val (user, group) = createUserAndGroup().unsafeRunSync()
      val talkId = Talk.Id.generate()
      cfpRepo.find(cfpData1.slug).unsafeRunSync() shouldBe None
      cfpRepo.list(group.id, page).unsafeRunSync().items shouldBe Seq()
      cfpRepo.listAvailable(talkId, page).unsafeRunSync().items shouldBe Seq()
      val cfp = cfpRepo.create(group.id, cfpData1, user.id, now).unsafeRunSync()
      cfpRepo.find(cfp.id).unsafeRunSync() shouldBe Some(cfp)
      cfpRepo.find(cfpData1.slug).unsafeRunSync() shouldBe Some(cfp)
      cfpRepo.list(group.id, page).unsafeRunSync().items shouldBe Seq(cfp)
      cfpRepo.listAvailable(talkId, page).unsafeRunSync().items shouldBe Seq(cfp)
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
        q.sql shouldBe "INSERT INTO cfps (id, group_id, slug, name, start, end, description, created, created_by, updated, updated_by) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)"
        check(q)
      }
      it("should build update") {
        val q = update(group.id, cfp.slug)(cfpData1, user.id, now)
        q.sql shouldBe "UPDATE cfps SET slug=?, name=?, start=?, end=?, description=?, updated=?, updated_by=? WHERE group_id=? AND slug=?"
        check(q)
      }
      it("should build selectOne for cfp id") {
        val q = selectOne(cfp.id)
        q.sql shouldBe "SELECT id, group_id, slug, name, start, end, description, created, created_by, updated, updated_by FROM cfps WHERE id=?"
        check(q)
      }
      it("should build selectOne for cfp slug") {
        val q = selectOne(cfp.slug)
        q.sql shouldBe "SELECT id, group_id, slug, name, start, end, description, created, created_by, updated, updated_by FROM cfps WHERE slug=?"
        check(q)
      }
      it("should build selectOne for group id and cfp slug") {
        val q = selectOne(group.id, cfp.slug)
        q.sql shouldBe "SELECT id, group_id, slug, name, start, end, description, created, created_by, updated, updated_by FROM cfps WHERE group_id=? AND slug=?"
        check(q)
      }
      it("should build selectOne for event id") {
        val q = selectOne(event.id)
        q.sql shouldBe "SELECT c.id, c.group_id, c.slug, c.name, c.start, c.end, c.description, c.created, c.created_by, c.updated, c.updated_by FROM cfps c INNER JOIN events e ON e.cfp_id=c.id WHERE e.id=?"
        check(q)
      }
      it("should build selectPage for a group") {
        val (s, c) = selectPage(group.id, params)
        s.sql shouldBe "SELECT id, group_id, slug, name, start, end, description, created, created_by, updated, updated_by FROM cfps WHERE group_id=? ORDER BY name OFFSET 0 LIMIT 20"
        c.sql shouldBe "SELECT count(*) FROM cfps WHERE group_id=? "
        check(s)
        check(c)
      }
      it("should build selectPage for a talk") {
        val (s, c) = selectPage(talk.id, params)
        s.sql shouldBe "SELECT id, group_id, slug, name, start, end, description, created, created_by, updated, updated_by FROM cfps WHERE id NOT IN (SELECT cfp_id FROM proposals WHERE talk_id = ?) ORDER BY name OFFSET 0 LIMIT 20"
        c.sql shouldBe "SELECT count(*) FROM cfps WHERE id NOT IN (SELECT cfp_id FROM proposals WHERE talk_id = ?) "
        check(s)
        check(c)
      }
      it("should build selectAll for group id") {
        val q = selectAll(group.id)
        q.sql shouldBe "SELECT id, group_id, slug, name, start, end, description, created, created_by, updated, updated_by FROM cfps WHERE group_id=?"
        check(q)
      }
    }
  }
}
