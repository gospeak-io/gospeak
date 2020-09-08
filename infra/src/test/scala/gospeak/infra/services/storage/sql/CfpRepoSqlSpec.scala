package gospeak.infra.services.storage.sql

import cats.data.NonEmptyList
import gospeak.core.domain.utils.FakeCtx
import gospeak.core.domain.{Event, Talk}
import gospeak.infra.services.storage.sql.CfpRepoSql._
import gospeak.infra.services.storage.sql.CfpRepoSqlSpec._
import gospeak.infra.services.storage.sql.EventRepoSqlSpec.{table => eventTable}
import gospeak.infra.services.storage.sql.testingutils.RepoSpec
import gospeak.infra.services.storage.sql.testingutils.RepoSpec.mapFields

class CfpRepoSqlSpec extends RepoSpec {
  describe("CfpRepoSql") {
    it("should handle crud operations") {
      val (user, group, ctx) = createOrga().unsafeRunSync()
      cfpRepo.list(params)(ctx).unsafeRunSync().items shouldBe List()
      val cfp = cfpRepo.create(cfpData1)(ctx).unsafeRunSync()
      cfpRepo.list(params)(ctx).unsafeRunSync().items shouldBe List(cfp)

      cfpRepo.edit(cfp.slug, cfpData2)(ctx).unsafeRunSync()
      cfpRepo.list(params)(ctx).unsafeRunSync().items.map(_.data) shouldBe List(cfpData2)
      // no delete...
    }
    it("should select a page") {
      val (user, group, cfp, talk, ctx) = createCfpAndTalk().unsafeRunSync()
      cfpRepo.list(params)(ctx).unsafeRunSync().items shouldBe List(cfp)
      cfpRepo.list(params.page(2))(ctx).unsafeRunSync().items shouldBe List()
      cfpRepo.list(params.pageSize(5))(ctx).unsafeRunSync().items shouldBe List(cfp)
      cfpRepo.list(params.search(cfp.name.value))(ctx).unsafeRunSync().items shouldBe List(cfp)
      cfpRepo.list(params.orderBy("slug"))(ctx).unsafeRunSync().items shouldBe List(cfp)
    }
    it("should be able to read correctly") {
      val (user, group, ctx) = createOrga().unsafeRunSync()
      val cfp = cfpRepo.create(cfpData1)(ctx).unsafeRunSync()

      cfpRepo.find(cfp.id).unsafeRunSync() shouldBe Some(cfp)
      cfpRepo.findRead(cfp.slug).unsafeRunSync() shouldBe Some(cfp)
      cfpRepo.find(cfp.slug)(ctx).unsafeRunSync() shouldBe Some(cfp)
      cfpRepo.find(Event.Id.generate()).unsafeRunSync() // shouldBe Some(cfp) // should create a liked event
      cfpRepo.findIncoming(cfp.slug)(ctx.userAwareCtx).unsafeRunSync() // shouldBe Some(cfp) // should have close null or after now
      cfpRepo.list(params)(ctx).unsafeRunSync().items shouldBe List(cfp)
      cfpRepo.availableFor(Talk.Id.generate(), params).unsafeRunSync().items shouldBe List(cfp)
      cfpRepo.list(List(cfp.id)).unsafeRunSync() shouldBe List(cfp)
      cfpRepo.list(cfp.group).unsafeRunSync() shouldBe List(cfp)
      cfpRepo.listAllIncoming(cfp.group)(ctx.userAwareCtx).unsafeRunSync() // shouldBe List(cfp) // should have close null or after now
      cfpRepo.listAllPublicSlugs()(ctx.userAwareCtx).unsafeRunSync() shouldBe List(cfp.slug)
      cfpRepo.listTags().unsafeRunSync() shouldBe cfp.tags
    }
    it("should fail to create a cfp when the group does not exists") {
      val user = userRepo.create(userData1, now, None).unsafeRunSync()
      val ctx = FakeCtx(now, user, group)
      an[Exception] should be thrownBy cfpRepo.create(cfpData1)(ctx).unsafeRunSync()
    }
    it("should fail to create two cfp for a group") {
      val (user, group, ctx) = createOrga().unsafeRunSync()
      cfpRepo.create(cfpData1)(ctx).unsafeRunSync()
      an[Exception] should be thrownBy cfpRepo.create(cfpData1)(ctx).unsafeRunSync()
    }
    it("should fail to create on duplicate slug") {
      val (user, group1, ctx1) = createOrga().unsafeRunSync()
      val group2 = groupRepo.create(groupData2)(ctx1).unsafeRunSync()
      cfpRepo.create(cfpData1)(FakeCtx(now, user, group1)).unsafeRunSync()
      an[Exception] should be thrownBy cfpRepo.create(cfpData1)(FakeCtx(now, user, group2)).unsafeRunSync()
    }
    it("should check queries") {
      check(insert(cfp), s"INSERT INTO ${table.stripSuffix(" c")} (${mapFields(fields, _.stripPrefix("c."))}) VALUES (${mapFields(fields, _ => "?")})")
      check(update(group.id, cfp.slug)(cfpData1, user.id, now), s"UPDATE $table SET slug=?, name=?, begin=?, close=?, description=?, tags=?, updated_at=?, updated_by=? WHERE c.group_id=? AND c.slug=?")
      check(selectOne(cfp.id), s"SELECT $fields FROM $table WHERE c.id=? $orderBy")
      check(selectOne(cfp.slug), s"SELECT $fields FROM $table WHERE c.slug=? $orderBy")
      check(selectOne(group.id, cfp.slug), s"SELECT $fields FROM $table WHERE c.group_id=? AND c.slug=? $orderBy")
      check(selectOne(event.id), s"SELECT $fields FROM $table INNER JOIN $eventTable ON c.id=e.cfp_id WHERE e.id=? $orderBy")
      check(selectOneIncoming(cfp.slug, now), s"SELECT $fields FROM $table WHERE (c.close IS NULL OR c.close > ?) AND c.slug=? $orderBy")
      check(selectPage(params), s"SELECT $fields FROM $table WHERE c.group_id=? $orderBy LIMIT 20 OFFSET 0")
      check(selectPage(talk.id, params), s"SELECT $fields FROM $table WHERE c.id NOT IN (SELECT p.cfp_id FROM proposals p WHERE p.talk_id=? ORDER BY p.created_at IS NULL, p.created_at DESC) $orderBy LIMIT 20 OFFSET 0")
      check(selectAll(group.id), s"SELECT $fields FROM $table WHERE c.group_id=? $orderBy")
      check(selectAll(NonEmptyList.of(cfp.id, cfp.id, cfp.id)), s"SELECT $fields FROM $table WHERE c.id IN (?, ?, ?)  $orderBy")
      check(selectAllPublicSlugs(), s"SELECT c.slug FROM $table $orderBy")
      check(selectAllIncoming(group.id, now), s"SELECT $fields FROM $table WHERE (c.close IS NULL OR c.close > ?) AND c.group_id=? $orderBy")
      check(selectTags(), s"SELECT c.tags FROM $table $orderBy")
    }
  }
}

object CfpRepoSqlSpec {
  val table = "cfps c"
  val fields: String = mapFields("id, group_id, slug, name, begin, close, description, tags, created_at, created_by, updated_at, updated_by", "c." + _)
  val orderBy = "ORDER BY c.close IS NULL, c.close DESC, c.name IS NULL, c.name"
}
