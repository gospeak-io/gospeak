package gospeak.infra.services.storage.sql

import cats.data.NonEmptyList
import gospeak.infra.services.storage.sql.SponsorPackRepoSql._
import gospeak.infra.services.storage.sql.SponsorPackRepoSqlSpec._
import gospeak.infra.services.storage.sql.testingutils.RepoSpec
import gospeak.infra.services.storage.sql.testingutils.RepoSpec.mapFields

class SponsorPackRepoSqlSpec extends RepoSpec {
  describe("SponsorPackRepoSql") {
    it("should handle crud operations") {
      val (user, group, ctx) = createOrga().unsafeRunSync()
      sponsorPackRepo.listAll(group.id).unsafeRunSync() shouldBe List()
      val sponsorPack = sponsorPackRepo.create(sponsorPackData1)(ctx).unsafeRunSync()
      sponsorPackRepo.listAll(group.id).unsafeRunSync() shouldBe List(sponsorPack)

      sponsorPackRepo.edit(sponsorPack.slug, sponsorPackData2)(ctx).unsafeRunSync()
      sponsorPackRepo.listAll(group.id).unsafeRunSync().map(_.data) shouldBe List(sponsorPackData2)
      // no delete...
    }
    it("should perform specific updates") {
      val (user, group, ctx) = createOrga().unsafeRunSync()
      val sponsorPack = sponsorPackRepo.create(sponsorPackData1)(ctx).unsafeRunSync()

      sponsorPackRepo.disable(sponsorPack.slug)(ctx).unsafeRunSync()
      sponsorPackRepo.find(sponsorPack.slug)(ctx).unsafeRunSync() shouldBe Some(sponsorPack.copy(active = false))

      sponsorPackRepo.enable(sponsorPack.slug)(ctx).unsafeRunSync()
      sponsorPackRepo.find(sponsorPack.slug)(ctx).unsafeRunSync() shouldBe Some(sponsorPack.copy(active = true))
    }
    it("should be able to read correctly") {
      val (user, group, ctx) = createOrga().unsafeRunSync()
      val sponsorPack = sponsorPackRepo.create(sponsorPackData1)(ctx).unsafeRunSync()

      sponsorPackRepo.find(sponsorPack.slug)(ctx).unsafeRunSync() shouldBe Some(sponsorPack)
      sponsorPackRepo.listAll(group.id).unsafeRunSync() shouldBe List(sponsorPack)
      sponsorPackRepo.listAll(ctx).unsafeRunSync() shouldBe List(sponsorPack)
      sponsorPackRepo.listActives(group.id).unsafeRunSync() shouldBe List(sponsorPack)
      sponsorPackRepo.listActives(ctx).unsafeRunSync() shouldBe List(sponsorPack)
    }
    it("should check queries") {
      check(insert(sponsorPack), s"INSERT INTO ${table.stripSuffix(" sp")} (${mapFields(fields, _.stripPrefix("sp."))}) VALUES (${mapFields(fields, _ => "?")})")
      check(update(group.id, sponsorPack.slug)(sponsorPack.data, user.id, now), s"UPDATE $table SET slug=?, name=?, description=?, price=?, currency=?, duration=?, updated_at=?, updated_by=? WHERE sp.group_id=? AND sp.slug=?")
      check(setActive(group.id, sponsorPack.slug)(active = true, user.id, now), s"UPDATE $table SET active=?, updated_at=?, updated_by=? WHERE sp.group_id=? AND sp.slug=?")
      check(selectOne(group.id, sponsorPack.slug), s"SELECT $fields FROM $table WHERE sp.group_id=? AND sp.slug=? $orderBy")
      check(selectAll(NonEmptyList.of(sponsorPack.id, sponsorPack.id)), s"SELECT $fields FROM $table WHERE sp.id IN (?, ?)  $orderBy")
      check(selectAll(group.id), s"SELECT $fields FROM $table WHERE sp.group_id=? $orderBy")
      check(selectActives(group.id), s"SELECT $fields FROM $table WHERE sp.group_id=? AND sp.active=? $orderBy")
    }
  }
}

object SponsorPackRepoSqlSpec {
  val table = "sponsor_packs sp"
  val fields: String = mapFields("id, group_id, slug, name, description, price, currency, duration, active, created_at, created_by, updated_at, updated_by", "sp." + _)
  val orderBy = "ORDER BY sp.active IS NULL, sp.active DESC, sp.price IS NULL, sp.price DESC"
}
