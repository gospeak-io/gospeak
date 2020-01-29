package gospeak.infra.services.storage.sql

import cats.data.NonEmptyList
import gospeak.core.domain.utils.FakeCtx
import gospeak.infra.services.storage.sql.GroupRepoSqlSpec._
import gospeak.infra.services.storage.sql.TablesSpec.socialFields
import gospeak.infra.services.storage.sql.UserRepoSqlSpec.{fields => userFields, table => userTable}
import gospeak.infra.services.storage.sql.testingutils.RepoSpec
import gospeak.infra.services.storage.sql.testingutils.RepoSpec.mapFields

class GroupRepoSqlSpec extends RepoSpec {
  describe("GroupRepoSql") {
    it("should create and retrieve a group") {
      val user = userRepo.create(userData1, now, None).unsafeRunSync()
      val ctx = FakeCtx(now, user)
      groupRepo.list(ctx).unsafeRunSync() shouldBe Seq()
      groupRepo.find(groupData1.slug).unsafeRunSync() shouldBe None
      val group = groupRepo.create(groupData1)(ctx).unsafeRunSync()
      groupRepo.list(ctx).unsafeRunSync() shouldBe Seq(group)
      groupRepo.find(groupData1.slug).unsafeRunSync() shouldBe Some(group)
    }
    it("should fail on duplicate slug") {
      val user = userRepo.create(userData1, now, None).unsafeRunSync()
      val ctx = FakeCtx(now, user)
      groupRepo.create(groupData1)(ctx).unsafeRunSync()
      an[Exception] should be thrownBy groupRepo.create(groupData1)(ctx).unsafeRunSync()
    }
    it("should add an owner") {
      val user1 = userRepo.create(userData1, now, None).unsafeRunSync()
      val user2 = userRepo.create(userData2, now, None).unsafeRunSync()
      val ctx1 = FakeCtx(now, user1)
      val created = groupRepo.create(groupData1)(ctx1).unsafeRunSync()
      created.owners.toList shouldBe List(user1.id)

      groupRepo.addOwner(created.id, user2.id, user1.id)(ctx1).unsafeRunSync()
      val updated = groupRepo.find(created.id).unsafeRunSync().get
      updated.owners.toList shouldBe List(user1.id, user2.id)
    }
    describe("Queries") {
      it("should build insert") {
        val q = GroupRepoSql.insert(group)
        check(q, s"INSERT INTO ${table.stripSuffix(" g")} (${mapFields(fieldsInsert, _.stripPrefix("g."))}) VALUES (${mapFields(fieldsInsert, _ => "?")})")
      }
      it("should build update") {
        val q = GroupRepoSql.update(group.slug)(group.data, user.id, now)
        check(q, s"UPDATE $table SET slug=?, name=?, logo=?, banner=?, contact=?, website=?, description=?, location=?, location_id=?, location_lat=?, location_lng=?, location_locality=?, location_country=?, " +
          s"social_facebook=?, social_instagram=?, social_twitter=?, social_linkedIn=?, social_youtube=?, social_meetup=?, social_eventbrite=?, social_slack=?, social_discord=?, social_github=?, " +
          s"tags=?, updated_at=?, updated_by=? WHERE g.slug=?")
      }
      it("should build updateOwners") {
        val q = GroupRepoSql.updateOwners(group.id)(NonEmptyList.of(user.id), user.id, now)
        check(q, s"UPDATE $table SET owners=?, updated_at=?, updated_by=? WHERE g.id=?")
      }
      it("should build selectPage") {
        val q = GroupRepoSql.selectPage(params)
        check(q, s"SELECT $fieldsFull FROM $tableFull $orderBy LIMIT 20 OFFSET 0")
      }
      it("should build selectPageJoinable") {
        val q = GroupRepoSql.selectPageJoinable(user.id, params)
        check(q, s"SELECT $fields FROM $table WHERE g.owners NOT LIKE ? $orderBy LIMIT 20 OFFSET 0")
      }
      it("should build selectPageJoined") {
        val q = GroupRepoSql.selectPageJoined(user.id, params)
        check(q, s"SELECT $fieldsWithMember FROM $tableWithMember WHERE gm.user_id=? $orderBy LIMIT 20 OFFSET 0")
      }
      it("should build selectAll by ids") {
        val q = GroupRepoSql.selectAll(NonEmptyList.of(group.id))
        check(q, s"SELECT $fields FROM $table WHERE g.id IN (?)  $orderBy")
      }
      it("should build selectAll by user") {
        val q = GroupRepoSql.selectAll(user.id)
        check(q, s"SELECT $fields FROM $table WHERE g.owners LIKE ? $orderBy")
      }
      it("should build selectOne with id") {
        val q = GroupRepoSql.selectOne(group.id)
        check(q, s"SELECT $fields FROM $table WHERE g.id=? $orderBy")
      }
      it("should build selectOne with slug") {
        val q = GroupRepoSql.selectOne(group.slug)
        check(q, s"SELECT $fields FROM $table WHERE g.slug=? $orderBy")
      }
      it("should build selectStats") {
        val q = GroupRepoSql.selectStats(group.slug)
        check(q, s"SELECT $statFields FROM $statTable WHERE g.slug=? GROUP BY g.id, g.slug, g.name $orderBy")
      }
      it("should build selectTags") {
        val q = GroupRepoSql.selectTags()
        check(q, s"SELECT g.tags FROM $table")
      }
      describe("member") {
        it("should build insertMember") {
          val q = GroupRepoSql.insertMember(member)
          check(q, s"INSERT INTO ${memberTable.stripSuffix(" gm")} (${mapFields(memberFields, _.stripPrefix("gm."))}) VALUES (${mapFields(memberFields, _ => "?")})")
        }
        it("should build disableMember") {
          val q = GroupRepoSql.disableMember(member, now)
          check(q, s"UPDATE $memberTable SET gm.leaved_at=? WHERE gm.group_id=? AND gm.user_id=?")
        }
        it("should build enableMember") {
          val q = GroupRepoSql.enableMember(member, now)
          check(q, s"UPDATE $memberTable SET gm.joined_at=?, gm.leaved_at=? WHERE gm.group_id=? AND gm.user_id=?")
        }
        it("should build selectPageActiveMembers") {
          val q = GroupRepoSql.selectPageActiveMembers(group.id, params)
          check(q, s"SELECT $memberFieldsWithUser FROM $memberTableWithUser WHERE gm.group_id=? AND gm.leaved_at IS NULL $memberOrderBy LIMIT 20 OFFSET 0")
        }
        it("should build selectAllActiveMembers") {
          val q = GroupRepoSql.selectAllActiveMembers(group.id)
          check(q, s"SELECT $memberFieldsWithUser FROM $memberTableWithUser WHERE gm.group_id=? AND gm.leaved_at IS NULL $memberOrderBy")
        }
        it("should build selectOneMember") {
          val q = GroupRepoSql.selectOneMember(group.id, user.id)
          check(q, s"SELECT $memberFieldsWithUser FROM $memberTableWithUser WHERE gm.group_id=? AND gm.user_id=? $memberOrderBy")
        }
        it("should build selectOneActiveMember") {
          val q = GroupRepoSql.selectOneActiveMember(group.id, user.id)
          check(q, s"SELECT $memberFieldsWithUser FROM $memberTableWithUser WHERE gm.group_id=? AND gm.user_id=? AND gm.leaved_at IS NULL $memberOrderBy")
        }
      }
    }
  }
}

object GroupRepoSqlSpec {
  val table = "groups g"
  val fieldsInsert: String = mapFields(s"id, slug, name, logo, banner, contact, website, description, location, location_id, location_lat, location_lng, location_locality, location_country, owners, $socialFields, tags, status, created_at, created_by, updated_at, updated_by", "g." + _)
  val fields: String = fieldsInsert.split(", ").filterNot(_.contains("location_")).mkString(", ")
  val orderBy = "ORDER BY g.name IS NULL, g.name"

  val memberTable = "group_members gm"
  private val memberFields = mapFields("group_id, user_id, role, presentation, joined_at, leaved_at", "gm." + _)
  private val memberOrderBy = "ORDER BY gm.joined_at IS NULL, gm.joined_at"

  private val tableFull = s"$table LEFT OUTER JOIN $memberTable ON g.id=gm.group_id AND gm.leaved_at IS NULL LEFT OUTER JOIN events e ON g.id=e.group_id AND e.published IS NOT NULL LEFT OUTER JOIN proposals p ON e.id=p.event_id GROUP BY $fields"
  private val fieldsFull = s"$fields, COALESCE(COUNT(DISTINCT gm.user_id), 0) as memberCount, COALESCE(COUNT(DISTINCT e.id), 0) as eventCount, COALESCE(COUNT(DISTINCT p.id), 0) as talkCount"

  private val memberTableWithUser = s"$memberTable INNER JOIN $userTable ON gm.user_id=u.id"
  private val memberFieldsWithUser = s"${memberFields.replaceAll("gm.user_id, ", "")}, $userFields"

  private val tableWithMember = s"$table INNER JOIN $memberTable ON g.id=gm.group_id INNER JOIN $userTable ON gm.user_id=u.id"
  private val fieldsWithMember = s"$fields, $memberFieldsWithUser"

  private val statTable = s"$table LEFT OUTER JOIN $memberTable ON g.id=gm.group_id AND gm.leaved_at IS NULL LEFT OUTER JOIN cfps c ON g.id=c.group_id LEFT OUTER JOIN proposals p ON c.id=p.cfp_id LEFT OUTER JOIN events e ON g.id=e.group_id"
  private val statFields = s"g.id, g.slug, g.name, COALESCE(COUNT(DISTINCT gm.user_id), 0) as memberCount, COALESCE(COUNT(DISTINCT p.id), 0) as proposalCount, COALESCE(COUNT(DISTINCT e.id), 0) as eventCount"
}
