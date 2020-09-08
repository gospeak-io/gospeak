package gospeak.infra.services.storage.sql

import cats.data.NonEmptyList
import gospeak.core.domain.{Group, User}
import gospeak.infra.services.storage.sql.GroupRepoSql._
import gospeak.infra.services.storage.sql.GroupRepoSqlSpec._
import gospeak.infra.services.storage.sql.TablesSpec.socialFields
import gospeak.infra.services.storage.sql.UserRepoSqlSpec.{fields => userFields, table => userTable}
import gospeak.infra.services.storage.sql.testingutils.RepoSpec
import gospeak.infra.services.storage.sql.testingutils.RepoSpec.mapFields
import gospeak.libs.scala.Extensions._

class GroupRepoSqlSpec extends RepoSpec {
  describe("GroupRepoSql") {
    it("should handle crud operations") {
      val (user, ctx) = createAdmin().unsafeRunSync()
      val user2 = userRepo.create(userData2, now, None).unsafeRunSync()
      groupRepo.list(params)(ctx).unsafeRunSync().items shouldBe List()
      val group = groupRepo.create(groupData1)(ctx).unsafeRunSync()
      groupRepo.list(params)(ctx).unsafeRunSync().items shouldBe List(group)

      groupRepo.edit(groupData2)(ctx.orgaCtx(group)).unsafeRunSync()
      groupRepo.list(params)(ctx).unsafeRunSync().items.map(_.data) shouldBe List(groupData2)
      // no delete...

      groupRepo.listMembers(group.id, params)(ctx.userAwareCtx).unsafeRunSync().items shouldBe List()
      val member = groupRepo.join(group.id)(user2, now).unsafeRunSync()
      groupRepo.listMembers(group.id, params)(ctx.userAwareCtx).unsafeRunSync().items shouldBe List(member)

      groupRepo.leave(member)(user2.id, now).unsafeRunSync()
      groupRepo.listMembers(group.id, params)(ctx.userAwareCtx).unsafeRunSync().items shouldBe List()
    }
    it("should fail on duplicate slug") {
      val (user, ctx) = createUser().unsafeRunSync()
      groupRepo.create(groupData1)(ctx).unsafeRunSync()
      an[Exception] should be thrownBy groupRepo.create(groupData1)(ctx).unsafeRunSync()
    }
    it("should perform specific updates") {
      val (user, group, ctx) = createOrga().unsafeRunSync()
      val user2 = userRepo.create(userData2, now, None).unsafeRunSync()
      group.owners shouldBe NonEmptyList.of(user.id)

      an[Exception] should be thrownBy groupRepo.addOwner(group.id, user.id, user.id)(ctx).unsafeRunSync()
      groupRepo.addOwner(group.id, user2.id, user.id)(ctx).unsafeRunSync()
      groupRepo.find(group.id).unsafeRunSync().map(_.owners) shouldBe Some(NonEmptyList.of(user.id, user2.id))

      an[Exception] should be thrownBy groupRepo.removeOwner(User.Id.generate())(ctx).unsafeRunSync()
      groupRepo.removeOwner(user.id)(ctx).unsafeRunSync()
      groupRepo.find(group.id).unsafeRunSync().map(_.owners) shouldBe Some(NonEmptyList.of(user2.id))
      an[Exception] should be thrownBy groupRepo.removeOwner(user2.id)(ctx).unsafeRunSync()
    }
    it("should select a page") {
      val (user, ctx) = createUser().unsafeRunSync()
      val user2 = userRepo.create(userData2, now, None).unsafeRunSync()
      val group1 = groupRepo.create(groupData1.copy(slug = Group.Slug.from("ddd").get, name = Group.Name("aaa")))(ctx).unsafeRunSync()
      val group2 = groupRepo.create(groupData2.copy(slug = Group.Slug.from("bbb").get, name = Group.Name("ccc")))(ctx).unsafeRunSync()
      val member1 = groupRepo.join(group1.id)(user, now).unsafeRunSync()
      val member12 = groupRepo.join(group1.id)(user2, now.plusSeconds(10)).unsafeRunSync()
      val member2 = groupRepo.join(group2.id)(user, now).unsafeRunSync()
      val groupFull1 = Group.Full(group1, 2, 0, 0)
      val groupFull2 = Group.Full(group2, 1, 0, 0)

      groupRepo.list(params)(ctx.adminCtx).unsafeRunSync().items shouldBe List(group1, group2)
      groupRepo.list(params.page(2))(ctx.adminCtx).unsafeRunSync().items shouldBe List()
      groupRepo.list(params.pageSize(5))(ctx.adminCtx).unsafeRunSync().items shouldBe List(group1, group2)
      groupRepo.list(params.search(group1.slug.value))(ctx.adminCtx).unsafeRunSync().items shouldBe List(group1)
      groupRepo.list(params.orderBy("slug"))(ctx.adminCtx).unsafeRunSync().items shouldBe List(group2, group1)

      groupRepo.listFull(params)(ctx.userAwareCtx).unsafeRunSync().items shouldBe List(groupFull1, groupFull2)
      groupRepo.listFull(params.page(2))(ctx.userAwareCtx).unsafeRunSync().items shouldBe List()
      groupRepo.listFull(params.pageSize(5))(ctx.userAwareCtx).unsafeRunSync().items shouldBe List(groupFull1, groupFull2)
      groupRepo.listFull(params.search(group1.slug.value))(ctx.userAwareCtx).unsafeRunSync().items shouldBe List(groupFull1)
      groupRepo.listFull(params.orderBy("slug"))(ctx.userAwareCtx).unsafeRunSync().items shouldBe List(groupFull2, groupFull1)

      groupRepo.listJoined(params)(ctx).unsafeRunSync().items shouldBe List(group1 -> member1, group2 -> member2)
      groupRepo.listJoined(params.page(2))(ctx).unsafeRunSync().items shouldBe List()
      groupRepo.listJoined(params.pageSize(5))(ctx).unsafeRunSync().items shouldBe List(group1 -> member1, group2 -> member2)
      groupRepo.listJoined(params.search(group1.slug.value))(ctx).unsafeRunSync().items shouldBe List(group1 -> member1)
      groupRepo.listJoined(params.orderBy("slug"))(ctx).unsafeRunSync().items shouldBe List(group2 -> member2, group1 -> member1)

      groupRepo.listMembers(group1.id, params)(ctx.userAwareCtx).unsafeRunSync().items shouldBe List(member1, member12)
      groupRepo.listMembers(group1.id, params.page(2))(ctx.userAwareCtx).unsafeRunSync().items shouldBe List()
      groupRepo.listMembers(group1.id, params.pageSize(5))(ctx.userAwareCtx).unsafeRunSync().items shouldBe List(member1, member12)
      groupRepo.listMembers(group1.id, params.search(member1.user.slug.value))(ctx.userAwareCtx).unsafeRunSync().items shouldBe List(member1)
      groupRepo.listMembers(group1.id, params.orderBy("-joined_at"))(ctx.userAwareCtx).unsafeRunSync().items shouldBe List(member12, member1)
    }
    it("should be able to read correctly") {
      val (user, group, ctx) = createOrga().unsafeRunSync()
      val member = groupRepo.join(group.id)(user, now).unsafeRunSync()
      val groupFull = Group.Full(group, 1, 0, 0)

      groupRepo.listAllSlugs().unsafeRunSync() shouldBe List(group.id -> group.slug)
      groupRepo.listFull(params)(ctx.userAwareCtx).unsafeRunSync().items shouldBe List(groupFull)
      groupRepo.listJoinable(params)(ctx).unsafeRunSync().items shouldBe List()
      groupRepo.list(params)(ctx.adminCtx).unsafeRunSync().items shouldBe List(group)
      groupRepo.list(user.id).unsafeRunSync() shouldBe List(group)
      groupRepo.listFull(user.id).unsafeRunSync() shouldBe List(groupFull)
      groupRepo.list(ctx).unsafeRunSync() shouldBe List(group)
      groupRepo.list(List(group.id)).unsafeRunSync() shouldBe List(group)
      groupRepo.listJoined(params)(ctx).unsafeRunSync().items shouldBe List(group -> member)
      groupRepo.find(group.id).unsafeRunSync() shouldBe Some(group)
      groupRepo.find(group.slug).unsafeRunSync() shouldBe Some(group)
      groupRepo.findFull(group.slug).unsafeRunSync() shouldBe Some(groupFull)
      groupRepo.exists(group.slug).unsafeRunSync() shouldBe true
      groupRepo.listTags().unsafeRunSync() shouldBe group.tags

      groupRepo.listMembers(ctx).unsafeRunSync() shouldBe List(member)
      groupRepo.listMembers(group.id, params)(ctx.userAwareCtx).unsafeRunSync().items shouldBe List(member)
      groupRepo.findActiveMember(group.id, user.id).unsafeRunSync() shouldBe Some(member)
      groupRepo.getStats(ctx).unsafeRunSync() shouldBe Group.Stats(group.id, group.slug, group.name, 1, 0, 0)
    }
    it("should check queries") {
      check(insert(group), s"INSERT INTO ${table.stripSuffix(" g")} (${mapFields(fieldsInsert, _.stripPrefix("g."))}) VALUES (${mapFields(fieldsInsert, _ => "?")})")
      check(update(group.slug)(group.data, user.id, now), s"UPDATE $table SET slug=?, name=?, logo=?, banner=?, contact=?, website=?, description=?, location=?, location_id=?, location_lat=?, location_lng=?, location_locality=?, location_country=?, social_facebook=?, social_instagram=?, social_twitter=?, social_linkedIn=?, social_youtube=?, social_meetup=?, social_eventbrite=?, social_slack=?, social_discord=?, social_github=?, tags=?, updated_at=?, updated_by=? WHERE g.slug=?")
      check(updateOwners(group.id)(NonEmptyList.of(user.id), user.id, now), s"UPDATE $table SET owners=?, updated_at=?, updated_by=? WHERE g.id=?")
      check(selectAllSlugs(), s"SELECT g.id, g.slug FROM $table $orderBy")
      check(selectPage(params)(adminCtx), s"SELECT $fields FROM $table $orderBy LIMIT 20 OFFSET 0")
      check(selectPageFull(params), s"SELECT $fieldsFull FROM $tableFull GROUP BY $fields $orderBy LIMIT 20 OFFSET 0")
      check(selectPageJoinable(params), s"SELECT $fields FROM $table WHERE g.owners NOT LIKE ? $orderBy LIMIT 20 OFFSET 0")
      check(selectPageJoined(params), s"SELECT $fieldsWithMember FROM $tableWithMember WHERE gm.user_id=? $orderBy LIMIT 20 OFFSET 0")
      check(selectAll(user.id), s"SELECT $fields FROM $table WHERE g.owners LIKE ? $orderBy")
      check(selectAllFull(user.id), s"SELECT $fieldsFull FROM $tableFull WHERE g.owners LIKE ? GROUP BY $fields $orderBy")
      check(selectAll(NonEmptyList.of(group.id)), s"SELECT $fields FROM $table WHERE g.id IN (?)  $orderBy")
      check(selectOne(group.id), s"SELECT $fields FROM $table WHERE g.id=? $orderBy")
      check(selectOne(group.slug), s"SELECT $fields FROM $table WHERE g.slug=? $orderBy")
      check(selectOneFull(group.slug), s"SELECT $fieldsFull FROM $tableFull WHERE g.slug=? GROUP BY $fields $orderBy")
      check(selectStats(group.slug), s"SELECT $statFields FROM $statTable WHERE g.slug=? GROUP BY g.id, g.slug, g.name $orderBy")
      check(selectTags(), s"SELECT g.tags FROM $table $orderBy")

      check(insertMember(member), s"INSERT INTO ${memberTable.stripSuffix(" gm")} (${mapFields(memberFields, _.stripPrefix("gm."))}) VALUES (${mapFields(memberFields, _ => "?")})")
      check(disableMember(member, now), s"UPDATE $memberTable SET leaved_at=? WHERE gm.group_id=? AND gm.user_id=?")
      check(enableMember(member, now), s"UPDATE $memberTable SET joined_at=?, leaved_at=? WHERE gm.group_id=? AND gm.user_id=?")
      check(selectPageActiveMembers(group.id, params), s"SELECT $memberFieldsWithUser FROM $memberTableWithUser WHERE gm.group_id=? AND gm.leaved_at IS NULL $memberOrderBy LIMIT 20 OFFSET 0")
      check(selectAllActiveMembers(group.id), s"SELECT $memberFieldsWithUser FROM $memberTableWithUser WHERE gm.group_id=? AND gm.leaved_at IS NULL $memberOrderBy")
      check(selectOneMember(group.id, user.id), s"SELECT $memberFieldsWithUser FROM $memberTableWithUser WHERE gm.group_id=? AND gm.user_id=? $memberOrderBy")
      check(selectOneActiveMember(group.id, user.id), s"SELECT $memberFieldsWithUser FROM $memberTableWithUser WHERE gm.group_id=? AND gm.user_id=? AND gm.leaved_at IS NULL $memberOrderBy")
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

  private val tableFull = s"$table LEFT OUTER JOIN $memberTable ON g.id=gm.group_id AND gm.leaved_at IS NULL LEFT OUTER JOIN events e ON g.id=e.group_id AND e.published IS NOT NULL LEFT OUTER JOIN proposals p ON e.id=p.event_id"
  private val fieldsFull = s"$fields, COALESCE(COUNT(DISTINCT gm.user_id), 0) as memberCount, COALESCE(COUNT(DISTINCT e.id), 0) as eventCount, COALESCE(COUNT(DISTINCT p.id), 0) as talkCount"

  private val memberTableWithUser = s"$memberTable INNER JOIN $userTable ON gm.user_id=u.id"
  private val memberFieldsWithUser = s"${memberFields.replaceAll("gm.user_id, ", "")}, $userFields"

  private val tableWithMember = s"$table INNER JOIN $memberTable ON g.id=gm.group_id INNER JOIN $userTable ON gm.user_id=u.id"
  private val fieldsWithMember = s"$fields, $memberFieldsWithUser"

  private val statTable = s"$table LEFT OUTER JOIN $memberTable ON g.id=gm.group_id AND gm.leaved_at IS NULL LEFT OUTER JOIN events e ON g.id=e.group_id LEFT OUTER JOIN cfps c ON g.id=c.group_id LEFT OUTER JOIN proposals p ON c.id=p.cfp_id"
  private val statFields = s"g.id, g.slug, g.name, COALESCE(COUNT(DISTINCT gm.user_id), 0) as memberCount, COALESCE(COUNT(DISTINCT e.id), 0) as eventCount, COALESCE(COUNT(DISTINCT p.id), 0) as proposalCount"
}
