package fr.gospeak.infra.services.storage.sql

import cats.data.NonEmptyList
import fr.gospeak.infra.services.storage.sql.GroupRepoSqlSpec._
import fr.gospeak.infra.services.storage.sql.UserRepoSqlSpec.{fields => userFields, table => userTable}
import fr.gospeak.infra.services.storage.sql.testingutils.RepoSpec
import fr.gospeak.infra.services.storage.sql.testingutils.RepoSpec.mapFields

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
        check(q, s"INSERT INTO ${table.stripSuffix(" g")} (${mapFields(fields, _.stripPrefix("g."))}) VALUES (${mapFields(fields, _ => "?")})")
      }
      it("should build update") {
        val q = GroupRepoSql.update(group.slug)(group.data, user.id, now)
        check(q, s"UPDATE $table SET g.slug=?, g.name=?, g.contact=?, g.description=?, g.tags=?, g.updated=?, g.updated_by=? WHERE g.slug=?")
      }
      it("should build updateOwners") {
        val q = GroupRepoSql.updateOwners(group.id)(NonEmptyList.of(user.id), user.id, now)
        check(q, s"UPDATE $table SET g.owners=?, g.updated=?, g.updated_by=? WHERE g.id=?")
      }
      it("should build selectPage") {
        val q = GroupRepoSql.selectPage(params)
        check(q, s"SELECT $fields FROM $table ORDER BY g.name IS NULL, g.name OFFSET 0 LIMIT 20")
      }
      it("should build selectPageJoinable") {
        val q = GroupRepoSql.selectPageJoinable(user.id, params)
        check(q, s"SELECT $fields FROM $table WHERE g.owners NOT LIKE ? ORDER BY g.name IS NULL, g.name OFFSET 0 LIMIT 20")
      }
      it("should build selectAll") {
        val q = GroupRepoSql.selectAll(user.id)
        check(q, s"SELECT $fields FROM $table WHERE g.owners LIKE ?")
      }
      it("should build selectOne") {
        val q = GroupRepoSql.selectOne(user.id, group.slug)
        check(q, s"SELECT $fields FROM $table WHERE g.owners LIKE ? AND g.slug=?")
      }
      it("should build selectOne with id") {
        val q = GroupRepoSql.selectOne(group.id)
        check(q, s"SELECT $fields FROM $table WHERE g.id=?")
      }
      it("should build selectOne with slug") {
        val q = GroupRepoSql.selectOne(group.slug)
        check(q, s"SELECT $fields FROM $table WHERE g.slug=?")
      }
      it("should build selectTags") {
        val q = GroupRepoSql.selectTags()
        check(q, s"SELECT g.tags FROM $table")
      }
      describe("member") {
        it("should build insertMember") {
          val q = GroupRepoSql.insertMember(member)
          check(q, s"INSERT INTO $memberTable ($memberFields) VALUES (${mapFields(memberFields, _ => "?")})")
        }
        it("should build selectPageMembers") {
          val q = GroupRepoSql.selectPageMembers(group.id, params)
          check(q, s"SELECT $memberFieldsWithUser FROM $memberTableWithUser WHERE gm.group_id=? ORDER BY gm.joined_at IS NULL, gm.joined_at OFFSET 0 LIMIT 20")
        }
        it("should build selectOneMember") {
          val q = GroupRepoSql.selectOneMember(group.id, user.id)
          check(q, s"SELECT $memberFieldsWithUser FROM $memberTableWithUser WHERE gm.group_id=? AND gm.user_id=?")
        }
      }
    }
  }
}

object GroupRepoSqlSpec {
  val table = "groups g"
  val fields = "g.id, g.slug, g.name, g.contact, g.description, g.owners, g.tags, g.created, g.created_by, g.updated, g.updated_by"

  private val memberTable = "group_members"
  private val memberFields = "group_id, user_id, role, presentation, joined_at"
  private val memberTableWithUser = s"$memberTable gm INNER JOIN $userTable u ON gm.user_id=u.id"
  private val memberFieldsWithUser = s"${mapFields(memberFields.replaceAll("user_id, ", ""), "gm." + _)}, ${mapFields(userFields, "u." + _)}"
}
