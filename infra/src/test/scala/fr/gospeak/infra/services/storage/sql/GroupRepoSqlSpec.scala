package fr.gospeak.infra.services.storage.sql

import cats.data.NonEmptyList
import fr.gospeak.core.domain.Group
import fr.gospeak.infra.services.storage.sql.GroupRepoSqlSpec._
import fr.gospeak.infra.services.storage.sql.testingutils.RepoSpec
import fr.gospeak.infra.services.storage.sql.testingutils.RepoSpec.mapFields
import fr.gospeak.infra.services.storage.sql.UserRepoSqlSpec.{fields => userFields, table => userTable}

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
        val q = GroupRepoSql.selectPage(params)
        q.query.sql shouldBe s"SELECT $fields FROM $table ORDER BY name IS NULL, name OFFSET 0 LIMIT 20"
        q.count.sql shouldBe s"SELECT count(*) FROM $table "
        check(q.query)
        check(q.count)
      }
      it("should build selectPageJoinable") {
        val q = GroupRepoSql.selectPageJoinable(user.id, params)
        q.query.sql shouldBe s"SELECT $fields FROM $table WHERE owners NOT LIKE ? ORDER BY name IS NULL, name OFFSET 0 LIMIT 20"
        q.count.sql shouldBe s"SELECT count(*) FROM $table WHERE owners NOT LIKE ? "
        check(q.query)
        check(q.count)
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
      describe("member") {
        it("should build insertMember") {
          val q = GroupRepoSql.insertMember(group, user, Group.Member.Role.Owner, None, now)
          q.sql shouldBe s"INSERT INTO $memberTable ($memberFields) VALUES (${mapFields(memberFields, _ => "?")})"
          check(q)
        }
        it("should build selectPageMembers") {
          val q = GroupRepoSql.selectPageMembers(group.id, params)
          q.query.sql shouldBe s"SELECT $memberFieldsWithUser FROM $memberTableWithUser WHERE m.group_id=? ORDER BY m.joined_at IS NULL, m.joined_at OFFSET 0 LIMIT 20"
          q.count.sql shouldBe s"SELECT count(*) FROM $memberTableWithUser WHERE m.group_id=? "
          check(q.query)
          check(q.count)
        }
        it("should build selectOneMember") {
          val q = GroupRepoSql.selectOneMember(group.id, user.id)
          q.sql shouldBe s"SELECT $memberFieldsWithUser FROM $memberTableWithUser WHERE m.group_id=? AND m.user_id=?"
          check(q)
        }
      }
    }
  }
}

object GroupRepoSqlSpec {
  val table = "groups"
  val fields = "id, slug, name, contact, description, owners, tags, created, created_by, updated, updated_by"

  private val memberTable = "group_members"
  private val memberFields = "group_id, user_id, role, presentation, joined_at"
  private val memberTableWithUser = s"$memberTable m INNER JOIN $userTable u ON m.user_id=u.id"
  private val memberFieldsWithUser = s"${mapFields(userFields, "u." + _)}, ${mapFields(memberFields.stripPrefix("group_id, user_id, "), "m." + _)}"
}
