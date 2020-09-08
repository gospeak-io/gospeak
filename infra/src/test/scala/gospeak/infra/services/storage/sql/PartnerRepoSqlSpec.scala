package gospeak.infra.services.storage.sql

import cats.data.NonEmptyList
import gospeak.core.domain.Partner
import gospeak.infra.services.storage.sql.ContactRepoSqlSpec.{table => contactTable}
import gospeak.infra.services.storage.sql.PartnerRepoSqlSpec._
import gospeak.infra.services.storage.sql.SponsorRepoSqlSpec.{table => sponsorTable}
import gospeak.infra.services.storage.sql.TablesSpec.socialFields
import gospeak.infra.services.storage.sql.VenueRepoSqlSpec.{table => venueTable}
import gospeak.infra.services.storage.sql.testingutils.RepoSpec
import gospeak.infra.services.storage.sql.testingutils.RepoSpec.mapFields
import gospeak.libs.scala.Extensions._

class PartnerRepoSqlSpec extends RepoSpec {
  describe("PartnerRepoSql") {
    it("should handle crud operations") {
      val (user, group, ctx) = createOrga().unsafeRunSync()
      partnerRepo.list(group.id).unsafeRunSync() shouldBe List()
      val partner = partnerRepo.create(partnerData1)(ctx).unsafeRunSync()
      partnerRepo.list(group.id).unsafeRunSync() shouldBe List(partner)

      partnerRepo.edit(partner.slug, partnerData2)(ctx).unsafeRunSync()
      partnerRepo.list(group.id).unsafeRunSync().map(_.data) shouldBe List(partnerData2)

      partnerRepo.remove(partnerData2.slug)(ctx).unsafeRunSync()
      partnerRepo.list(group.id).unsafeRunSync() shouldBe List()
    }
    it("should fail on duplicate slug") {
      val (user, group, ctx) = createOrga().unsafeRunSync()
      partnerRepo.create(partnerData1)(ctx).unsafeRunSync()
      an[Exception] should be thrownBy partnerRepo.create(partnerData1)(ctx).unsafeRunSync()
    }
    it("should select a page") {
      val (user, group, ctx) = createOrga().unsafeRunSync()
      val partner1 = partnerRepo.create(partnerData1.copy(slug = Partner.Slug.from("ddd").get, name = Partner.Name("aaa")))(ctx).unsafeRunSync()
      val partner2 = partnerRepo.create(partnerData2.copy(slug = Partner.Slug.from("bbb").get, name = Partner.Name("ccc")))(ctx).unsafeRunSync()
      val contact = contactRepo.create(contactData1.copy(partner = partner1.id))(ctx).unsafeRunSync()
      val venue = venueRepo.create(partner1.id, venueData1.copy(contact = venueData1.contact.map(_ => contact.id)))(ctx).unsafeRunSync()
      val sponsorPack = sponsorPackRepo.create(sponsorPackData1)(ctx).unsafeRunSync()
      val sponsor = sponsorRepo.create(sponsorData1.copy(partner = partner1.id, pack = sponsorPack.id, contact = sponsorData1.contact.map(_ => contact.id)))(ctx).unsafeRunSync()
      val dbSponsor = sponsorRepo.find(sponsor.id)(ctx).unsafeRunSync().get // FIXME needed until LocalDate read/write is fixed
      val event = eventRepo.create(eventData1.copy(venue = Some(venue.id)))(ctx).unsafeRunSync()
      val partnerFull1 = Partner.Full(partner1, venueCount = 1, sponsorCount = 1, lastSponsorDate = Some(dbSponsor.finish), contactCount = 1, eventCount = 1, lastEventDate = Some(event.start))
      val partnerFull2 = Partner.Full(partner2, venueCount = 0, sponsorCount = 0, lastSponsorDate = None, contactCount = 0, eventCount = 0, lastEventDate = None)

      partnerRepo.list(params)(ctx).unsafeRunSync().items shouldBe List(partner1, partner2)
      partnerRepo.list(params.page(2))(ctx).unsafeRunSync().items shouldBe List()
      partnerRepo.list(params.pageSize(5))(ctx).unsafeRunSync().items shouldBe List(partner1, partner2)
      partnerRepo.list(params.search(partner1.name.value))(ctx).unsafeRunSync().items shouldBe List(partner1)
      partnerRepo.list(params.orderBy("slug"))(ctx).unsafeRunSync().items shouldBe List(partner2, partner1)

      partnerRepo.listFull(params)(ctx).unsafeRunSync().items shouldBe List(partnerFull1, partnerFull2)
      partnerRepo.listFull(params.page(2))(ctx).unsafeRunSync().items shouldBe List()
      partnerRepo.listFull(params.pageSize(5))(ctx).unsafeRunSync().items shouldBe List(partnerFull1, partnerFull2)
      partnerRepo.listFull(params.search(partner1.name.value))(ctx).unsafeRunSync().items shouldBe List(partnerFull1)
      partnerRepo.listFull(params.orderBy("slug"))(ctx).unsafeRunSync().items shouldBe List(partnerFull2, partnerFull1)
      partnerRepo.listFull(params.filters("venues" -> "true"))(ctx).unsafeRunSync().items shouldBe List(partnerFull1)
      partnerRepo.listFull(params.filters("sponsors" -> "true"))(ctx).unsafeRunSync().items shouldBe List(partnerFull1)
      partnerRepo.listFull(params.filters("contacts" -> "true"))(ctx).unsafeRunSync().items shouldBe List(partnerFull1)
      partnerRepo.listFull(params.filters("events" -> "true"))(ctx).unsafeRunSync().items shouldBe List(partnerFull1)
    }
    it("should be able to read correctly") {
      val (user, group, ctx) = createOrga().unsafeRunSync()
      val partner = partnerRepo.create(partnerData1)(ctx).unsafeRunSync()
      val partnerFull = Partner.Full(partner, venueCount = 0, sponsorCount = 0, lastSponsorDate = None, contactCount = 0, eventCount = 0, lastEventDate = None)

      partnerRepo.list(params)(ctx).unsafeRunSync().items shouldBe List(partner)
      partnerRepo.listFull(params)(ctx).unsafeRunSync().items shouldBe List(partnerFull)
      partnerRepo.list(group.id).unsafeRunSync() shouldBe List(partner)
      partnerRepo.list(List(partner.id)).unsafeRunSync() shouldBe List(partner)
      partnerRepo.find(partner.id)(ctx).unsafeRunSync() shouldBe Some(partner)
      partnerRepo.find(partner.slug)(ctx).unsafeRunSync() shouldBe Some(partner)
      partnerRepo.find(group.id, partner.slug).unsafeRunSync() shouldBe Some(partner)
    }
    it("should check queries") {
      check(PartnerRepoSql.insert(partner), s"INSERT INTO ${table.stripSuffix(" pa")} (${mapFields(fields, _.stripPrefix("pa."))}) VALUES (${mapFields(fields, _ => "?")})")
      check(PartnerRepoSql.update(group.id, partner.slug)(partner.data, user.id, now), s"UPDATE $table SET slug=?, name=?, notes=?, description=?, logo=?, " +
        s"social_facebook=?, social_instagram=?, social_twitter=?, social_linkedIn=?, social_youtube=?, social_meetup=?, social_eventbrite=?, social_slack=?, social_discord=?, social_github=?, " +
        s"updated_at=?, updated_by=? WHERE pa.group_id=? AND pa.slug=?")
      check(PartnerRepoSql.delete(group.id, partner.slug), s"DELETE FROM $table WHERE pa.group_id=? AND pa.slug=?")
      check(PartnerRepoSql.selectPage(params), s"SELECT $fields FROM $table WHERE pa.group_id=? $orderBy LIMIT 20 OFFSET 0")
      check(PartnerRepoSql.selectPageFull(params), s"SELECT $fieldsFull FROM $tableFull WHERE pa.group_id=? $groupByFull $orderByFull LIMIT 20 OFFSET 0")
      check(PartnerRepoSql.selectAll(group.id), s"SELECT $fields FROM $table WHERE pa.group_id=? $orderBy")
      check(PartnerRepoSql.selectAll(NonEmptyList.of(partner.id)), s"SELECT $fields FROM $table WHERE pa.id IN (?)  $orderBy")
      check(PartnerRepoSql.selectOne(group.id, partner.id), s"SELECT $fields FROM $table WHERE pa.group_id=? AND pa.id=? $orderBy")
      check(PartnerRepoSql.selectOne(group.id, partner.slug), s"SELECT $fields FROM $table WHERE pa.group_id=? AND pa.slug=? $orderBy")
    }
  }
}

object PartnerRepoSqlSpec {
  val table = "partners pa"
  val fields: String = mapFields(s"id, group_id, slug, name, notes, description, logo, $socialFields, created_at, created_by, updated_at, updated_by", "pa." + _)
  val orderBy = "ORDER BY pa.name IS NULL, pa.name"

  private val tableFull = s"$table LEFT OUTER JOIN $venueTable ON pa.id=v.partner_id LEFT OUTER JOIN $sponsorTable ON pa.id=s.partner_id LEFT OUTER JOIN $contactTable ON pa.id=ct.partner_id LEFT OUTER JOIN events e ON v.id=e.venue"
  private val fieldsFull = s"$fields, COALESCE(COUNT(DISTINCT v.id), 0) as venueCount, COALESCE(COUNT(DISTINCT s.id), 0) as sponsorCount, MAX(s.finish) as lastSponsorDate, COALESCE(COUNT(DISTINCT ct.id), 0) as contactCount, COALESCE(COUNT(DISTINCT e.id), 0) as eventCount, MAX(e.start) as lastEventDate"
  private val groupByFull = s"GROUP BY $fields"
  private val orderByFull = "ORDER BY LOWER(pa.name) IS NULL, LOWER(pa.name)"
}
