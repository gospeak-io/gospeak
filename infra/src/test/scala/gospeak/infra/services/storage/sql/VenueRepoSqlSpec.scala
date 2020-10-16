package gospeak.infra.services.storage.sql

import cats.data.NonEmptyList
import gospeak.core.domain.{Partner, Venue}
import gospeak.infra.services.storage.sql.ContactRepoSqlSpec.{fields => contactFields, table => contactTable}
import gospeak.infra.services.storage.sql.EventRepoSqlSpec.{table => eventTable}
import gospeak.infra.services.storage.sql.GroupRepoSqlSpec.{table => groupTable}
import gospeak.infra.services.storage.sql.PartnerRepoSqlSpec.{fields => partnerFields, table => partnerTable}
import gospeak.infra.services.storage.sql.VenueRepoSql._
import gospeak.infra.services.storage.sql.VenueRepoSqlSpec._
import gospeak.infra.services.storage.sql.testingutils.RepoSpec
import gospeak.libs.scala.domain.Markdown

class VenueRepoSqlSpec extends RepoSpec {
  describe("VenueRepoSql") {
    it("should handle crud operations") {
      val (user, group, ctx) = createOrga().unsafeRunSync()
      val partner = partnerRepo.create(partnerData1)(ctx).unsafeRunSync()
      val contact1 = venueData1.contact.map(_ => contactRepo.create(contactData1.copy(partner = partner.id))(ctx).unsafeRunSync())
      val contact2 = venueData2.contact.map(_ => contactRepo.create(contactData2.copy(partner = partner.id))(ctx).unsafeRunSync())
      venueRepo.listAllFull()(ctx).unsafeRunSync() shouldBe List()
      val venueData = venueData1.copy(contact = contact1.map(_.id))
      val venue = venueRepo.create(partner.id, venueData)(ctx).unsafeRunSync()
      venue.data shouldBe venueData
      val venueFull = Venue.Full(venue, partner, contact1)
      venueRepo.listAllFull()(ctx).unsafeRunSync() shouldBe List(venueFull)

      val data = venueData2.copy(contact = contact2.map(_.id))
      venueRepo.edit(venue.id, data)(ctx).unsafeRunSync()
      venueRepo.listAllFull()(ctx).unsafeRunSync().map(_.data) shouldBe List(data)

      venueRepo.remove(venue.id)(ctx).unsafeRunSync()
      venueRepo.listAllFull()(ctx).unsafeRunSync() shouldBe List()
    }
    it("should duplicate a public venue") {
      val (user, group, ctx) = createOrga().unsafeRunSync()
      val (user2, group2, ctx2) = createOrga(credentials2, userData2, groupData2).unsafeRunSync()
      val partner1 = partnerRepo.create(partnerData1)(ctx).unsafeRunSync()
      val contact1 = venueData1.contact.map(_ => contactRepo.create(contactData1.copy(partner = partner1.id))(ctx).unsafeRunSync())
      val venue1 = venueRepo.create(partner1.id, venueData1.copy(contact = contact1.map(_.id)))(ctx).unsafeRunSync()
      val event1 = eventRepo.create(eventData1.copy(venue = Some(venue1.id)))(ctx).unsafeRunSync()
      eventRepo.publish(event1.slug)(ctx).unsafeRunSync()

      val (partner2, venue2, contact2) = venueRepo.duplicate(venue1.id)(ctx2).unsafeRunSync()

      partner2.data shouldBe partner1.data.copy(notes = Markdown(""))
      venue2.data shouldBe venue1.data.copy(contact = venue2.contact, notes = Markdown(""), refs = Venue.ExtRefs())
      contact2.map(_.data) shouldBe contact1.map(_.data.copy(partner = partner2.id, notes = Markdown("")))
    }
    it("should select a page") {
      val (user, group, ctx) = createOrga().unsafeRunSync()
      val (user2, group2, ctx2) = createOrga(credentials2, userData2, groupData2).unsafeRunSync()
      val partner1 = partnerRepo.create(partnerData1.copy(name = Partner.Name("aaaaaa")))(ctx).unsafeRunSync()
      val contact1 = venueData1.contact.map(_ => contactRepo.create(contactData1.copy(partner = partner1.id))(ctx).unsafeRunSync())
      val venue1 = venueRepo.create(partner1.id, venueData1.copy(contact = contact1.map(_.id), roomSize = Some(2)))(ctx).unsafeRunSync()
      val event1 = eventRepo.create(eventData1.copy(venue = Some(venue1.id)))(ctx).unsafeRunSync()
      eventRepo.publish(event1.slug)(ctx).unsafeRunSync()
      val partner2 = partnerRepo.create(partnerData2.copy(name = Partner.Name("bbbbbb")))(ctx).unsafeRunSync()
      val contact2 = venueData2.contact.map(_ => contactRepo.create(contactData2.copy(partner = partner2.id))(ctx).unsafeRunSync())
      val venue2 = venueRepo.create(partner2.id, venueData2.copy(contact = contact2.map(_.id), roomSize = Some(1)))(ctx.plusSeconds(10)).unsafeRunSync()
      val event2 = eventRepo.create(eventData2.copy(venue = Some(venue2.id)))(ctx).unsafeRunSync()
      eventRepo.publish(event2.slug)(ctx).unsafeRunSync()
      val venueFull1 = Venue.Full(venue1, partner1, contact1)
      val venueFull2 = Venue.Full(venue2, partner2, contact2)
      val venuePublic1 = Venue.Public(venueFull1, 1)
      val venuePublic2 = Venue.Public(venueFull2, 1)
      val venueCommon1 = Venue.Common(venueFull1, 0, public = false)
      val venueCommon2 = Venue.Common(venueFull2, 0, public = false)

      venueRepo.listFull(params)(ctx).unsafeRunSync().items shouldBe List(venueFull1, venueFull2)
      venueRepo.listFull(params.page(2))(ctx).unsafeRunSync().items shouldBe List()
      venueRepo.listFull(params.pageSize(5))(ctx).unsafeRunSync().items shouldBe List(venueFull1, venueFull2)
      venueRepo.listFull(params.search(partner1.name.value))(ctx).unsafeRunSync().items shouldBe List(venueFull1)
      venueRepo.listFull(params.orderBy("room_size"))(ctx).unsafeRunSync().items shouldBe List(venueFull2, venueFull1)

      venueRepo.listPublic(params)(ctx2).unsafeRunSync().items shouldBe List(venuePublic1, venuePublic2)
      venueRepo.listPublic(params.page(2))(ctx2).unsafeRunSync().items shouldBe List()
      venueRepo.listPublic(params.pageSize(5))(ctx2).unsafeRunSync().items shouldBe List(venuePublic1, venuePublic2)
      venueRepo.listPublic(params.search(partner1.name.value))(ctx2).unsafeRunSync().items shouldBe List(venuePublic1)
      venueRepo.listPublic(params.orderBy("-name"))(ctx2).unsafeRunSync().items shouldBe List(venuePublic2, venuePublic1)

      venueRepo.listCommon(params)(ctx).unsafeRunSync().items shouldBe List(venueCommon1, venueCommon2)
      venueRepo.listCommon(params.page(2))(ctx).unsafeRunSync().items shouldBe List()
      venueRepo.listCommon(params.pageSize(5))(ctx).unsafeRunSync().items shouldBe List(venueCommon1, venueCommon2)
      venueRepo.listCommon(params.search(partner1.name.value))(ctx).unsafeRunSync().items shouldBe List(venueCommon1)
      venueRepo.listCommon(params.orderBy("-name"))(ctx).unsafeRunSync().items shouldBe List(venueCommon2, venueCommon1)
    }
    it("should be able to read correctly") {
      val (user, group, ctx) = createOrga().unsafeRunSync()
      val (user2, group2, ctx2) = createOrga(credentials2, userData2, groupData2).unsafeRunSync()
      val partner = partnerRepo.create(partnerData1)(ctx).unsafeRunSync()
      val contact = venueData1.contact.map(_ => contactRepo.create(contactData1.copy(partner = partner.id))(ctx).unsafeRunSync())
      val venue = venueRepo.create(partner.id, venueData1.copy(contact = contact.map(_.id)))(ctx).unsafeRunSync()
      val event = eventRepo.create(eventData1.copy(venue = Some(venue.id)))(ctx).unsafeRunSync()
      eventRepo.publish(event.slug)(ctx).unsafeRunSync()
      val venueFull = Venue.Full(venue, partner, contact)
      val venuePublic = Venue.Public(venueFull, 1)
      val venueCommon = Venue.Common(venueFull, 0, public = false)

      venueRepo.findFull(venue.id)(ctx).unsafeRunSync() shouldBe Some(venueFull)
      venueRepo.findPublic(venue.id)(ctx2).unsafeRunSync() shouldBe Some(venuePublic)
      venueRepo.listFull(params)(ctx).unsafeRunSync().items shouldBe List(venueFull)
      venueRepo.listPublic(params)(ctx2).unsafeRunSync().items shouldBe List(venuePublic)
      venueRepo.listCommon(params)(ctx).unsafeRunSync().items shouldBe List(venueCommon)
      venueRepo.listAllFull()(ctx).unsafeRunSync() shouldBe List(venueFull)
      venueRepo.listAllFull(group.id).unsafeRunSync() shouldBe List(venueFull)
      venueRepo.listAllFull(group.id, List(venue.id)).unsafeRunSync() shouldBe List(venueFull)
      venueRepo.listAllFull(partner.id).unsafeRunSync() shouldBe List(venueFull)
      contact.foreach(c => venueRepo.listAll(c.id)(ctx).unsafeRunSync() shouldBe List(venue))
    }
    it("should check queries") {
      check(insert(venue), s"INSERT INTO ${table.stripSuffix(" v")} (${mapFields(fieldsInsert, _.stripPrefix("v."))}) VALUES (${mapFields(fieldsInsert, _ => "?")})")
      check(update(group.id, venue.id)(venue.data, user.id, now), s"UPDATE $table SET contact_id=?, address=?, address_id=?, address_lat=?, address_lng=?, address_locality=?, address_country=?, notes=?, room_size=?, meetupGroup=?, meetupVenue=?, updated_at=?, updated_by=? WHERE v.id=(SELECT v.id FROM $tableFull WHERE pa.group_id=? AND v.id=? $orderByFull)")
      check(delete(group.id, venue.id), s"DELETE FROM $table WHERE v.id=(SELECT v.id FROM $tableFull WHERE pa.group_id=? AND v.id=? $orderByFull)")
      check(selectOneFull(venue.id), s"SELECT $fieldsFull FROM $tableFull WHERE v.id=? $orderBy")
      check(selectOneFull(group.id, venue.id), s"SELECT $fieldsFull FROM $tableFull WHERE pa.group_id=? AND v.id=? $orderBy")
      check(selectOnePublic(group.id, venue.id), s"SELECT $fieldsPublic FROM $tablePublic WHERE v.id=? GROUP BY pa.slug, pa.name, pa.logo, v.address $orderByPublic")
      check(selectPageFull(params), s"SELECT $fieldsFull FROM $tableFull WHERE pa.group_id=? $orderBy LIMIT 20 OFFSET 0")
      check(selectPagePublic(params), s"SELECT $fieldsPublic FROM $tablePublic GROUP BY pa.slug, pa.name, pa.logo, v.address $orderByPublic LIMIT 20 OFFSET 0")
      unsafeCheck(selectPageCommon(params), s"SELECT $commonFields FROM $commonTable $commonOrderBy LIMIT 20 OFFSET 0")
      check(selectAllFull(group.id), s"SELECT $fieldsFull FROM $tableFull WHERE pa.group_id=? $orderBy")
      check(selectAllFull(group.id, NonEmptyList.of(venue.id)), s"SELECT $fieldsFull FROM $tableFull WHERE pa.group_id=? AND v.id IN (?) $orderBy")
      check(selectAllFull(partner.id), s"SELECT $fieldsFull FROM $tableFull WHERE v.partner_id=? $orderBy")
      check(selectAll(group.id, contact.id), s"SELECT $fields FROM $table WHERE v.contact_id=? $orderBy")
    }
  }
}

object VenueRepoSqlSpec {

  import RepoSpec._

  val table = "venues v"
  val fieldsInsert: String = mapFields("id, partner_id, contact_id, address, address_id, address_lat, address_lng, address_locality, address_country, notes, room_size, meetupGroup, meetupVenue, created_at, created_by, updated_at, updated_by", "v." + _)
  val fields: String = mapFields("id, partner_id, contact_id, address, notes, room_size, meetupGroup, meetupVenue, created_at, created_by, updated_at, updated_by", "v." + _)
  val orderBy = "ORDER BY v.created_at IS NULL, v.created_at"

  private val tableWithPartner = s"$table INNER JOIN $partnerTable ON v.partner_id=pa.id"

  private val tableFull = s"$tableWithPartner LEFT OUTER JOIN $contactTable ON v.contact_id=ct.id"
  private val fieldsFull = s"$fields, $partnerFields, $contactFields"
  private val orderByFull = "ORDER BY v.created_at IS NULL, v.created_at"

  private val tablePublic = s"$tableWithPartner INNER JOIN $groupTable ON pa.group_id=g.id AND g.id != ? INNER JOIN $eventTable ON g.id=e.group_id AND e.venue=v.id AND e.published IS NOT NULL"
  private val fieldsPublic = s"MAX(v.id) as id, pa.slug, pa.name, pa.logo, v.address, COALESCE(COUNT(e.id), 0) as events"
  private val orderByPublic = "ORDER BY pa.name IS NULL, pa.name"

  private val commonTable = s"(" +
    s"(SELECT v.id, pa.slug, pa.name, pa.logo, v.address, 0 as events, false as public FROM $tableWithPartner WHERE pa.group_id=? ORDER BY v.created_at IS NULL, v.created_at) UNION " +
    s"(SELECT $fieldsPublic, true as public FROM $tablePublic GROUP BY pa.slug, pa.name, pa.logo, v.address, public ORDER BY pa.name IS NULL, pa.name)) v"
  private val commonFields = "v.id, v.slug, v.name, v.logo, v.address, v.events, v.public"
  private val commonOrderBy = "ORDER BY v.public IS NULL, v.public, v.name IS NULL, v.name, v.events IS NULL, v.events DESC"
}
