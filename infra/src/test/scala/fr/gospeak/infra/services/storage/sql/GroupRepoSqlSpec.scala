package fr.gospeak.infra.services.storage.sql

import cats.data.NonEmptyList
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
        val q = GroupRepoSql.insert(group)
        q.sql shouldBe s"INSERT INTO $table ($fields) VALUES (${mapFields(fields, _ => "?")})"
        check(q)
      }
      it("should build update") {
        val q = GroupRepoSql.update(group.slug)(group.data, user.id, now)
        q.sql shouldBe s"UPDATE $table SET slug=?, name=?, contact=?, description=?, tags=?, updated=?, updated_by=? WHERE slug=?"
        check(q)
      }
      it("should build updateOwners") {
        val q = GroupRepoSql.updateOwners(group.id)(NonEmptyList.of(user.id), user.id, now)
        q.sql shouldBe s"UPDATE $table SET owners=?, updated=?, updated_by=? WHERE id=?"
        check(q)
      }
      it("should build selectPage") {
        val (s, c) = GroupRepoSql.selectPage(params)
        s.sql shouldBe s"SELECT $fields FROM $table ORDER BY name IS NULL, name OFFSET 0 LIMIT 20"
        c.sql shouldBe s"SELECT count(*) FROM $table "
        check(s)
        check(c)
      }
      it("should build selectPageJoinable") {
        val (s, c) = GroupRepoSql.selectPageJoinable(user.id, params)
        s.sql shouldBe s"SELECT $fields FROM $table WHERE owners NOT LIKE ? ORDER BY name IS NULL, name OFFSET 0 LIMIT 20"
        c.sql shouldBe s"SELECT count(*) FROM $table WHERE owners NOT LIKE ? "
        check(s)
        check(c)
      }
      it("should build selectAll") {
        val q = GroupRepoSql.selectAll(user.id)
        q.sql shouldBe s"SELECT $fields FROM $table WHERE owners LIKE ?"
        check(q)
      }
      it("should build selectOne") {
        val q = GroupRepoSql.selectOne(user.id, group.slug)
        q.sql shouldBe s"SELECT $fields FROM $table WHERE owners LIKE ? AND slug=?"
        check(q)
      }
      it("should build selectOne with id") {
        val q = GroupRepoSql.selectOne(group.id)
        q.sql shouldBe s"SELECT $fields FROM $table WHERE id=?"
        check(q)
      }
      it("should build selectOne with slug") {
        val q = GroupRepoSql.selectOne(group.slug)
        q.sql shouldBe s"SELECT $fields FROM $table WHERE slug=?"
        check(q)
      }
      it("should build selectTags") {
        val q = GroupRepoSql.selectTags()
        q.sql shouldBe s"SELECT tags FROM $table"
        check(q)
      }
    }
  }
}

object GroupRepoSqlSpec {
  val table = "groups"
  val fields = "id, slug, name, contact, description, owners, tags, created, created_by, updated, updated_by"
}
