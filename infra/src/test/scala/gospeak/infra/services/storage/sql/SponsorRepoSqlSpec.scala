package gospeak.infra.services.storage.sql

import gospeak.core.domain.{Sponsor, SponsorPack}
import gospeak.infra.services.storage.sql.ContactRepoSqlSpec.{fields => contactFields, table => contactTable}
import gospeak.infra.services.storage.sql.PartnerRepoSqlSpec.{fields => partnerFields, table => partnerTable}
import gospeak.infra.services.storage.sql.SponsorPackRepoSqlSpec.{fields => sponsorPackFields, table => sponsorPackTable}
import gospeak.infra.services.storage.sql.SponsorRepoSqlSpec._
import gospeak.infra.services.storage.sql.testingutils.RepoSpec
import gospeak.libs.scala.TimeUtils
import gospeak.libs.scala.domain.Price._

class SponsorRepoSqlSpec extends RepoSpec {
  describe("SponsorRepoSql") {
    it("should handle crud operations") { // error saving LocalDate :(
      val (user, group, ctx) = createOrga().unsafeRunSync()
      val (partner, venue, contact) = createPartnerAndVenue()(ctx).unsafeRunSync()
      val sponsorPack = sponsorPackRepo.create(sponsorPackData1)(ctx).unsafeRunSync()
      sponsorRepo.listAll(ctx).unsafeRunSync() shouldBe List()
      val sponsorData = sponsorData1.copy(partner = partner.id, pack = sponsorPack.id, contact = contact.map(_.id))
      val sponsor = sponsorRepo.create(sponsorData)(ctx).unsafeRunSync()
      sponsor.data shouldBe sponsorData
      sponsorRepo.listAll(ctx).unsafeRunSync() shouldBe List(sponsor)

      val data = sponsorData2.copy(partner = partner.id, pack = sponsorPack.id, contact = contact.map(_.id))
      sponsorRepo.edit(sponsor.id, data)(ctx).unsafeRunSync()
      sponsorRepo.listAll(ctx).unsafeRunSync().map(_.data) shouldBe List(data)

      sponsorRepo.remove(sponsor.id)(ctx).unsafeRunSync()
      sponsorRepo.listAll(ctx).unsafeRunSync() shouldBe List()
    }
    it("should select a page") { // error saving LocalDate :(
      val (user, group, ctx) = createOrga().unsafeRunSync()
      val (partner, venue, contact) = createPartnerAndVenue()(ctx).unsafeRunSync()
      val sponsorPack1 = sponsorPackRepo.create(sponsorPackData1.copy(name = SponsorPack.Name("aaaaaa")))(ctx).unsafeRunSync()
      val sponsorPack2 = sponsorPackRepo.create(sponsorPackData2)(ctx).unsafeRunSync()
      val date = nowLDT.toLocalDate
      val data1 = sponsorData1.copy(partner = partner.id, pack = sponsorPack1.id, contact = contact.map(_.id), start = date.minusDays(10), finish = date.plusDays(10), price = 20.eur)
      val data2 = sponsorData2.copy(partner = partner.id, pack = sponsorPack2.id, contact = None, start = date.minusDays(20), finish = date.minusDays(10), price = 10.eur)
      val sponsor1 = sponsorRepo.create(data1)(ctx).unsafeRunSync()
      val sponsor2 = sponsorRepo.create(data2)(ctx).unsafeRunSync()
      val sponsorFull1 = Sponsor.Full(sponsor1, sponsorPack1, partner, contact)
      val sponsorFull2 = Sponsor.Full(sponsor2, sponsorPack2, partner, None)

      sponsorRepo.listFull(params)(ctx).unsafeRunSync().items shouldBe List(sponsorFull1, sponsorFull2)
      sponsorRepo.listFull(params.page(2))(ctx).unsafeRunSync().items shouldBe List()
      sponsorRepo.listFull(params.pageSize(5))(ctx).unsafeRunSync().items shouldBe List(sponsorFull1, sponsorFull2)
      sponsorRepo.listFull(params.search(sponsorPack1.name.value))(ctx).unsafeRunSync().items shouldBe List(sponsorFull1)
      sponsorRepo.listFull(params.orderBy("price"))(ctx).unsafeRunSync().items shouldBe List(sponsorFull2, sponsorFull1)
      sponsorRepo.listFull(params.filters("active" -> "true"))(ctx).unsafeRunSync().items shouldBe List(sponsorFull1)
      sponsorRepo.listFull(params.filters("active" -> "false"))(ctx).unsafeRunSync().items shouldBe List(sponsorFull2)
    }
    it("should be able to read correctly") { // error saving LocalDate :(
      val (user, group, ctx) = createOrga().unsafeRunSync()
      val (partner, venue, contact) = createPartnerAndVenue()(ctx).unsafeRunSync()
      val sponsorPack = sponsorPackRepo.create(sponsorPackData1)(ctx).unsafeRunSync()
      val sponsor = sponsorRepo.create(sponsorData1.copy(partner = partner.id, pack = sponsorPack.id, contact = contact.map(_.id), finish = sponsorData1.start.plusYears(1)))(ctx).unsafeRunSync()
      val sponsorFull = Sponsor.Full(sponsor, sponsorPack, partner, contact)

      sponsorRepo.find(sponsor.id)(ctx).unsafeRunSync() shouldBe Some(sponsor)
      sponsorRepo.listFull(params)(ctx).unsafeRunSync().items shouldBe List(sponsorFull)
      sponsorRepo.listCurrentFull(group.id, TimeUtils.toInstant(sponsorData1.start.plusDays(10))).unsafeRunSync() shouldBe List(sponsorFull)
      sponsorRepo.listAll(ctx).unsafeRunSync() shouldBe List(sponsor)
      contact.foreach(c => sponsorRepo.listAll(c.id)(ctx).unsafeRunSync() shouldBe List(sponsor))
      sponsorRepo.listAllFull(partner.id)(ctx).unsafeRunSync() shouldBe List(sponsorFull)
    }
    it("should check queries") {
      check(SponsorRepoSql.insert(sponsor), s"INSERT INTO ${table.stripSuffix(" s")} (${mapFields(fields, _.stripPrefix("s."))}) VALUES (${mapFields(fields, _ => "?")})")
      check(SponsorRepoSql.update(group.id, sponsor.id)(sponsor.data, user.id, now), s"UPDATE $table SET partner_id=?, sponsor_pack_id=?, contact_id=?, start=?, finish=?, paid=?, price=?, currency=?, updated_at=?, updated_by=? WHERE s.group_id=? AND s.id=?")
      check(SponsorRepoSql.delete(group.id, sponsor.id), s"DELETE FROM $table WHERE s.group_id=? AND s.id=?")
      check(SponsorRepoSql.selectOne(group.id, sponsor.id), s"SELECT $fields FROM $table WHERE s.group_id=? AND s.id=? $orderBy")
      check(SponsorRepoSql.selectPage(params), s"SELECT $fieldsFull FROM $tableFull WHERE s.group_id=? $orderBy LIMIT 20 OFFSET 0")
      unsafeCheck(SponsorRepoSql.selectCurrent(group.id, now), s"SELECT $fieldsFull FROM $tableFull WHERE s.group_id=? AND s.start < ? AND s.finish > ? $orderBy")
      check(SponsorRepoSql.selectAll(group.id), s"SELECT $fields FROM $table WHERE s.group_id=? $orderBy")
      check(SponsorRepoSql.selectAll(group.id, contact.id), s"SELECT $fields FROM $table WHERE s.group_id=? AND s.contact_id=? $orderBy")
      check(SponsorRepoSql.selectAllFull(group.id, partner.id), s"SELECT $fieldsFull FROM $tableFull WHERE s.group_id=? AND s.partner_id=? $orderBy")
    }
  }
}

object SponsorRepoSqlSpec {

  import RepoSpec._

  val table = "sponsors s"
  val fields: String = mapFields("id, group_id, partner_id, sponsor_pack_id, contact_id, start, finish, paid, price, currency, created_at, created_by, updated_at, updated_by", "s." + _)
  val orderBy = "ORDER BY s.start IS NULL, s.start DESC"

  private val tableFull = s"$table INNER JOIN $sponsorPackTable ON s.sponsor_pack_id=sp.id INNER JOIN $partnerTable ON s.partner_id=pa.id LEFT OUTER JOIN $contactTable ON s.contact_id=ct.id"
  private val fieldsFull = s"$fields, $sponsorPackFields, $partnerFields, $contactFields"
}
