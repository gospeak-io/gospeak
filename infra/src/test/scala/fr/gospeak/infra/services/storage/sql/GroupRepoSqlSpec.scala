package fr.gospeak.infra.services.storage.sql

import cats.data.NonEmptyList
import fr.gospeak.infra.services.storage.sql.GroupRepoSql._
import fr.gospeak.infra.services.storage.sql.GroupRepoSqlSpec._
import fr.gospeak.infra.services.storage.sql.testingutils.RepoSpec

class GroupRepoSqlSpec extends RepoSpec {
  describe("GroupRepoSql") {
    it("should create and retrieve a group") {
      val user = userRepo.create(userData1, now).unsafeRunSync()
      groupRepo.list(user.id).unsafeRunSync() shouldBe Seq()
      groupRepo.find(user.id, groupData1.slug).unsafeRunSync() shouldBe None
      val group = groupRepo.create(groupData1, user.id, now).unsafeRunSync()
      groupRepo.list(user.id).unsafeRunSync() shouldBe Seq(group)
      groupRepo.find(user.id, groupData1.slug).unsafeRunSync() shouldBe Some(group)
    }
    it("should not retrieve not owned groups") {
      val user1 = userRepo.create(userData1, now).unsafeRunSync()
      val user2 = userRepo.create(userData2, now).unsafeRunSync()
      groupRepo.create(groupData1, user2.id, now).unsafeRunSync()
      groupRepo.list(user1.id).unsafeRunSync() shouldBe Seq()
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
      it("should build update") {
        val q = update(group.slug)(group.data, user.id, now)
        q.sql shouldBe s"UPDATE groups SET slug=?, name=?, contact=?, description=?, tags=?, updated=?, updated_by=? WHERE slug=?"
        check(q)
      }
      it("should build updateOwners") {
        val q = updateOwners(group.id)(NonEmptyList.of(user.id), user.id, now)
        q.sql shouldBe s"UPDATE groups SET owners=?, updated=?, updated_by=? WHERE id=?"
        check(q)
      }
      it("should build selectPage") {
        val (s, c) = selectPage(params)
        s.sql shouldBe s"SELECT $fieldList FROM groups ORDER BY name IS NULL, name OFFSET 0 LIMIT 20"
        c.sql shouldBe "SELECT count(*) FROM groups "
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
      it("should build selectOne with slug") {
        val q = selectOne(group.slug)
        q.sql shouldBe s"SELECT $fieldList FROM groups WHERE slug=?"
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
  val fieldList = "id, slug, name, contact, description, owners, tags, created, created_by, updated, updated_by"
}
