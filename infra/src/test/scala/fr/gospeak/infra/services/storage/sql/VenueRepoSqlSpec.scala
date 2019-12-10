package fr.gospeak.infra.services.storage.sql

import cats.data.NonEmptyList
import fr.gospeak.infra.services.storage.sql.ContactRepoSqlSpec.{fields => contactFields, table => contactTable}
import fr.gospeak.infra.services.storage.sql.EventRepoSqlSpec.{table => eventTable}
import fr.gospeak.infra.services.storage.sql.GroupRepoSqlSpec.{table => groupTable}
import fr.gospeak.infra.services.storage.sql.PartnerRepoSqlSpec.{fields => partnerFields, table => partnerTable}
import fr.gospeak.infra.services.storage.sql.VenueRepoSqlSpec._
import fr.gospeak.infra.services.storage.sql.testingutils.RepoSpec

class VenueRepoSqlSpec extends RepoSpec {
  describe("VenueRepoSql") {
    describe("Queries") {
      it("should build insert") {
        val q = VenueRepoSql.insert(venue)
        check(q, s"INSERT INTO ${table.stripSuffix(" v")} (${mapFields(fieldsInsert, _.stripPrefix("v."))}) VALUES (${mapFields(fieldsInsert, _ => "?")})")
      }
      it("should build update") {
        val q = VenueRepoSql.update(group.id, venue.id)(venue.data, user.id, now)
        check(q, s"UPDATE $table SET contact_id=?, address=?, address_id=?, address_lat=?, address_lng=?, address_locality=?, address_country=?, notes=?, room_size=?, meetupGroup=?, meetupVenue=?, updated_at=?, updated_by=? WHERE v.id=(SELECT v.id FROM $tableFull WHERE pa.group_id=? AND v.id=?)")
      }
      it("should build delete") {
        val q = VenueRepoSql.delete(group.id, venue.id)
        check(q, s"DELETE FROM $table WHERE v.id=(SELECT v.id FROM $tableFull WHERE pa.group_id=? AND v.id=?)")
      }
      it("should build selectOneFull") {
        val q = VenueRepoSql.selectOneFull(venue.id)
        check(q, s"SELECT $fieldsFull FROM $tableFull WHERE v.id=? $orderBy")
      }
      it("should build selectOneFull in a group") {
        val q = VenueRepoSql.selectOneFull(group.id, venue.id)
        check(q, s"SELECT $fieldsFull FROM $tableFull WHERE pa.group_id=? AND v.id=? $orderBy")
      }
      it("should build selectPageFull") {
        val q = VenueRepoSql.selectPageFull(group.id, params)
        check(q, s"SELECT $fieldsFull FROM $tableFull WHERE pa.group_id=? $orderBy LIMIT 20 OFFSET 0")
      }
      it("should build selectAllFull for group id") {
        val q = VenueRepoSql.selectAllFull(group.id)
        check(q, s"SELECT $fieldsFull FROM $tableFull WHERE pa.group_id=? $orderBy")
      }
      it("should build selectAllFull for partner id") {
        val q = VenueRepoSql.selectAllFull(partner.id)
        check(q, s"SELECT $fieldsFull FROM $tableFull WHERE v.partner_id=? $orderBy")
      }
      it("should build selectAllFull for group id and ids") {
        val q = VenueRepoSql.selectAllFull(group.id, NonEmptyList.of(venue.id))
        check(q, s"SELECT $fieldsFull FROM $tableFull WHERE pa.group_id=? AND v.id IN (?)  $orderBy")
      }
      it("should build selectAll for contact") {
        val q = VenueRepoSql.selectAll(group.id, contact.id)
        check(q, s"SELECT $fields FROM $table WHERE v.contact_id=? $orderBy")
      }
      it("should build selectPagePublic") {
        val q = VenueRepoSql.selectPagePublic(group.id, params)
        check(q, s"SELECT $fieldsPublic FROM $tablePublic GROUP BY pa.slug, pa.name, pa.logo, v.address $orderByPublic LIMIT 20 OFFSET 0")
      }
      it("should build selectOnePublic") {
        val q = VenueRepoSql.selectOnePublic(group.id, venue.id)
        check(q, s"SELECT $fieldsPublic FROM $tablePublic WHERE v.id=? GROUP BY pa.slug, pa.name, pa.logo, v.address $orderByPublic")
      }
      it("should build selectPageCommon") {
        val q = VenueRepoSql.selectPageCommon(group.id, params)
        q.fr.query.sql shouldBe s"SELECT $commonFields FROM $commonTable $commonOrderBy LIMIT 20 OFFSET 0"
        // ignored because of fake nullable columns
        // check(q, s"SELECT $commonFields FROM $commonTable $commonOrderBy LIMIT 20 OFFSET 0")
      }
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

  private val tablePublic = s"$tableWithPartner INNER JOIN $groupTable ON pa.group_id=g.id AND g.id != ? INNER JOIN $eventTable ON g.id=e.group_id AND e.venue=v.id AND e.published IS NOT NULL"
  private val fieldsPublic = s"pa.slug, pa.name, pa.logo, v.address, MAX(v.id) as id, COALESCE(COUNT(e.id), 0) as events"
  private val orderByPublic = "ORDER BY pa.name IS NULL, pa.name"

  private val commonTable = s"(" +
    s"(SELECT false as public, pa.slug, pa.name, pa.logo, v.address, v.id, 0 as events FROM $tableWithPartner WHERE pa.group_id=?) UNION " +
    s"(SELECT true as public, $fieldsPublic FROM $tablePublic GROUP BY public, pa.slug, pa.name, pa.logo, v.address)) v"
  private val commonFields = "v.id, v.slug, v.name, v.logo, v.address, v.events, v.public"
  private val commonOrderBy = "ORDER BY v.public IS NULL, v.public, v.name IS NULL, v.name, v.events IS NULL, v.events DESC"
}
