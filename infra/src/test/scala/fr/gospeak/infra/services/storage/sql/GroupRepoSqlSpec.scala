package fr.gospeak.infra.services.storage.sql

import fr.gospeak.infra.services.storage.sql.GroupRepoSql._
import fr.gospeak.infra.services.storage.sql.testingutils.RepoSpec

class GroupRepoSqlSpec extends RepoSpec {
  describe("GroupRepoSql") {
    it("should create and retrieve a group") {
      val user = userRepo.create(userData1, now).unsafeRunSync()
      groupRepo.list(user.id, page).unsafeRunSync().items shouldBe Seq()
      groupRepo.find(user.id, groupData1.slug).unsafeRunSync() shouldBe None
      val group = groupRepo.create(groupData1, user.id, now).unsafeRunSync()
      groupRepo.list(user.id, page).unsafeRunSync().items shouldBe Seq(group)
      groupRepo.find(user.id, groupData1.slug).unsafeRunSync() shouldBe Some(group)
    }
    it("should not retrieve not owned groups") {
      val user1 = userRepo.create(userData1, now).unsafeRunSync()
      val user2 = userRepo.create(userData2, now).unsafeRunSync()
      groupRepo.create(groupData1, user2.id, now).unsafeRunSync()
      groupRepo.list(user1.id, page).unsafeRunSync().items shouldBe Seq()
      groupRepo.find(user1.id, groupData1.slug).unsafeRunSync() shouldBe None
    }
    it("should fail on duplicate slug") {
      val user = userRepo.create(userData1, now).unsafeRunSync()
      groupRepo.create(groupData1, user.id, now).unsafeRunSync()
      an[Exception] should be thrownBy groupRepo.create(groupData1, user.id, now).unsafeRunSync()
    }
    describe("Queries") {
      it("should build insert") {
        val q = insert(group)
        q.sql shouldBe "INSERT INTO groups (id, slug, name, description, owners, created, created_by, updated, updated_by) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)"
        check(q)
      }
      it("should build selectOne") {
        val q = selectOne(user.id, group.slug)
        q.sql shouldBe "SELECT id, slug, name, description, owners, created, created_by, updated, updated_by FROM groups WHERE owners LIKE ? AND slug=?"
        check(q)
      }
      it("should build selectPage") {
        val (s, c) = selectPage(user.id, params)
        s.sql shouldBe "SELECT id, slug, name, description, owners, created, created_by, updated, updated_by FROM groups WHERE owners LIKE ? ORDER BY name OFFSET 0 LIMIT 20"
        c.sql shouldBe "SELECT count(*) FROM groups WHERE owners LIKE ? "
        check(s)
        check(c)
      }
    }
  }
}
