package fr.gospeak.infra.services.storage.sql

import fr.gospeak.core.domain.{Group, Talk}
import CfpRepoSql._
import fr.gospeak.infra.services.storage.sql.testingutils.RepoSpec

class CfpRepoSqlSpec extends RepoSpec {
  describe("CfpRepoSql") {
    it("should create and retrieve a cfp for a group") {
      val (user, group) = createUserAndGroup().unsafeRunSync()
      val talkId = Talk.Id.generate()
      cfpRepo.listAvailable(talkId, page).unsafeRunSync().items shouldBe Seq()
      cfpRepo.find(cfpData.slug).unsafeRunSync() shouldBe None
      cfpRepo.find(group.id).unsafeRunSync() shouldBe None
      val cfp = cfpRepo.create(group.id, cfpData, user.id, now).unsafeRunSync()
      cfpRepo.listAvailable(talkId, page).unsafeRunSync().items shouldBe Seq(cfp)
      cfpRepo.find(cfpData.slug).unsafeRunSync() shouldBe Some(cfp)
      cfpRepo.find(cfp.id).unsafeRunSync() shouldBe Some(cfp)
      cfpRepo.find(group.id).unsafeRunSync() shouldBe Some(cfp)
    }
    it("should fail to create a cfp when the group does not exists") {
      val user = userRepo.create(userSlug, firstName, lastName, email, avatar, now).unsafeRunSync()
      an[Exception] should be thrownBy cfpRepo.create(Group.Id.generate(), cfpData, user.id, now).unsafeRunSync()
    }
    it("should fail to create two cfp for a group") {
      val (user, group) = createUserAndGroup().unsafeRunSync()
      cfpRepo.create(group.id, cfpData, user.id, now).unsafeRunSync()
      an[Exception] should be thrownBy cfpRepo.create(group.id, cfpData, user.id, now).unsafeRunSync()
    }
    it("should fail on duplicate slug") {
      val (user, group1) = createUserAndGroup().unsafeRunSync()
      val group2 = groupRepo.create(groupData2, user.id, now).unsafeRunSync()
      cfpRepo.create(group1.id, cfpData, user.id, now).unsafeRunSync()
      an[Exception] should be thrownBy cfpRepo.create(group2.id, cfpData, user.id, now).unsafeRunSync()
    }
    describe("Queries") {
      it("should build insert") {
        val q = insert(cfp)
        q.sql shouldBe "INSERT INTO cfps (id, group_id, slug, name, start, end, description, created, created_by, updated, updated_by) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)"
        check(q)
      }
      it("should build update") {
        val q = update(group.id, cfp.slug)(cfp.data, user.id, now)
        q.sql shouldBe "UPDATE cfps SET slug=?, name=?, start=?, end=?, description=?, updated=?, updated_by=? WHERE group_id=? AND slug=?"
        check(q)
      }
      it("should build selectOne for group id and cfp slug") {
        val q = selectOne(group.id, cfp.slug)
        q.sql shouldBe "SELECT id, group_id, slug, name, start, end, description, created, created_by, updated, updated_by FROM cfps WHERE group_id=? AND slug=?"
        check(q)
      }
      it("should build selectOne for cfp slug") {
        val q = selectOne(cfp.slug)
        q.sql shouldBe "SELECT id, group_id, slug, name, start, end, description, created, created_by, updated, updated_by FROM cfps WHERE slug=?"
        check(q)
      }
      it("should build selectOne for cfp id") {
        val q = selectOne(cfp.id)
        q.sql shouldBe "SELECT id, group_id, slug, name, start, end, description, created, created_by, updated, updated_by FROM cfps WHERE id=?"
        check(q)
      }
      it("should build selectOne for group id") {
        val q = selectOne(group.id)
        q.sql shouldBe "SELECT id, group_id, slug, name, start, end, description, created, created_by, updated, updated_by FROM cfps WHERE group_id=?"
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
    }
  }
}
