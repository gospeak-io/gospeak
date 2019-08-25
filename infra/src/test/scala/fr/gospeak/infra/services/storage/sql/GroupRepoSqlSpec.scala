package fr.gospeak.infra.services.storage.sql

import fr.gospeak.infra.services.storage.sql.GroupRepoSql._
import fr.gospeak.infra.services.storage.sql.GroupRepoSqlSpec._
import fr.gospeak.infra.services.storage.sql.testingutils.RepoSpec

class GroupRepoSqlSpec extends RepoSpec {
  describe("GroupRepoSql") {
    it("should create and retrieve a group") {
      val user = userRepo.create(userData1, now).unsafeRunSync()
      groupRepo.list(user.id, params).unsafeRunSync().items shouldBe Seq()
      groupRepo.find(user.id, groupData1.slug).unsafeRunSync() shouldBe None
      val group = groupRepo.create(groupData1, user.id, now).unsafeRunSync()
      groupRepo.list(user.id, params).unsafeRunSync().items shouldBe Seq(group)
      groupRepo.find(user.id, groupData1.slug).unsafeRunSync() shouldBe Some(group)
    }
    it("should not retrieve not owned groups") {
      val user1 = userRepo.create(userData1, now).unsafeRunSync()
      val user2 = userRepo.create(userData2, now).unsafeRunSync()
      groupRepo.create(groupData1, user2.id, now).unsafeRunSync()
      groupRepo.list(user1.id, params).unsafeRunSync().items shouldBe Seq()
      groupRepo.find(user1.id, groupData1.slug).unsafeRunSync() shouldBe None
    }
    it("should fail on duplicate slug") {
      val user = userRepo.create(userData1, now).unsafeRunSync()
      groupRepo.create(groupData1, user.id, now).unsafeRunSync()
      an[Exception] should be thrownBy groupRepo.create(groupData1, user.id, now).unsafeRunSync()
    }
    it("should add an owner") {
      val user1 = userRepo.create(userData1, now).unsafeRunSync()
      val user2 = userRepo.create(userData2, now).unsafeRunSync()
      val created = groupRepo.create(groupData1, user1.id, now).unsafeRunSync()
      created.owners.toList shouldBe List(user1.id)

      groupRepo.addOwner(created.id)(user2.id, user1.id, now).unsafeRunSync()
      val updated = groupRepo.find(created.id).unsafeRunSync().get
      updated.owners.toList shouldBe List(user1.id, user2.id)
    }
    describe("Queries") {
      it("should build insert") {
        val q = insert(group)
        q.sql shouldBe s"INSERT INTO groups ($fieldList) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)"
        check(q)
      }
      it("should build selectPage") {
        val (s, c) = selectPage(user.id, params)
        s.sql shouldBe s"SELECT $fieldList FROM groups WHERE owners LIKE ? ORDER BY name IS NULL, name OFFSET 0 LIMIT 20"
        c.sql shouldBe "SELECT count(*) FROM groups WHERE owners LIKE ? "
        check(s)
        check(c)
      }
      it("should build selectPagePublic") {
        val (s, c) = selectPagePublic(params)
        s.sql shouldBe s"SELECT $fieldList FROM groups WHERE published IS NOT NULL ORDER BY name IS NULL, name OFFSET 0 LIMIT 20"
        c.sql shouldBe "SELECT count(*) FROM groups WHERE published IS NOT NULL "
        check(s)
        check(c)
      }
      it("should build selectPagePublic with user") {
        val (s, c) = selectPagePublic(user.id, params)
        s.sql shouldBe s"SELECT $fieldList FROM groups WHERE published IS NOT NULL AND owners LIKE ? ORDER BY name IS NULL, name OFFSET 0 LIMIT 20"
        c.sql shouldBe "SELECT count(*) FROM groups WHERE published IS NOT NULL AND owners LIKE ? "
        check(s)
        check(c)
      }
      it("should build selectPageJoinable") {
        val (s, c) = selectPageJoinable(user.id, params)
        s.sql shouldBe s"SELECT $fieldList FROM groups WHERE owners NOT LIKE ? ORDER BY name IS NULL, name OFFSET 0 LIMIT 20"
        c.sql shouldBe "SELECT count(*) FROM groups WHERE owners NOT LIKE ? "
        check(s)
        check(c)
      }
      it("should build selectAll") {
        val q = selectAll(user.id)
        q.sql shouldBe s"SELECT $fieldList FROM groups WHERE owners LIKE ?"
        check(q)
      }
      it("should build selectOne") {
        val q = selectOne(user.id, group.slug)
        q.sql shouldBe s"SELECT $fieldList FROM groups WHERE owners LIKE ? AND slug=?"
        check(q)
      }
      it("should build selectOne with id") {
        val q = selectOne(group.id)
        q.sql shouldBe s"SELECT $fieldList FROM groups WHERE id=?"
        check(q)
      }
      it("should build selectOnePublic") {
        val q = selectOnePublic(group.slug)
        q.sql shouldBe s"SELECT $fieldList FROM groups WHERE published IS NOT NULL AND slug=?"
        check(q)
      }
      it("should build selectTags") {
        val q = selectTags()
        q.sql shouldBe s"SELECT tags FROM groups"
        check(q)
      }
    }
  }
}

object GroupRepoSqlSpec {
  val fieldList = "id, slug, name, description, owners, tags, published, created, created_by, updated, updated_by"
}
