package fr.gospeak.infra.services.storage.sql

import cats.data.NonEmptyList
import fr.gospeak.infra.services.storage.sql.ContactRepoSqlSpec.{fields => contactFields, table => contactTable}
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
        check(q, s"UPDATE $table SET v.contact_id=?, v.address=?, v.address_lat=?, v.address_lng=?, v.address_country=?, v.description=?, v.room_size=?, v.meetupGroup=?, v.meetupVenue=?, v.updated=?, v.updated_by=? WHERE v.id=(SELECT v.id FROM $tableFull WHERE pa.group_id=? AND v.id=?)")
      }
      it("should build selectOneFull") {
        val q = VenueRepoSql.selectOneFull(group.id, venue.id)
        check(q, s"SELECT $fieldsFull FROM $tableFull WHERE pa.group_id=? AND v.id=? $orderBy")
      }
      it("should build selectPageFull") {
        val q = VenueRepoSql.selectPageFull(group.id, params)
        check(q, s"SELECT $fieldsFull FROM $tableFull WHERE pa.group_id=? $orderBy OFFSET 0 LIMIT 20")
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
    }
  }
}

object VenueRepoSqlSpec {

  import RepoSpec._

  val table = "venues v"
  val fieldsInsert: String = mapFields("id, partner_id, contact_id, address, address_lat, address_lng, address_country, description, room_size, meetupGroup, meetupVenue, created, created_by, updated, updated_by", "v." + _)
  val fields: String = mapFields("id, partner_id, contact_id, address, description, room_size, meetupGroup, meetupVenue, created, created_by, updated, updated_by", "v." + _)
  val orderBy = "ORDER BY v.created IS NULL, v.created"

  private val tableFull = s"$table INNER JOIN $partnerTable ON v.partner_id=pa.id LEFT OUTER JOIN $contactTable ON v.contact_id=ct.id"
  private val fieldsFull = s"$fields, $partnerFields, $contactFields"
}
