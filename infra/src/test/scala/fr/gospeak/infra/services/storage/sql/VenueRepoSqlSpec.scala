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
        q.sql shouldBe s"INSERT INTO $table ($fieldsInsert) VALUES (${mapFields(fieldsInsert, _ => "?")})"
        check(q)
      }
      it("should build update") {
        val q = VenueRepoSql.update(group.id, venue.id)(venue.data, user.id, now)
        q.sql shouldBe s"UPDATE $table SET contact_id=?, address=?, address_lat=?, address_lng=?, address_country=?, description=?, room_size=?, meetupGroup=?, meetupVenue=?, updated=?, updated_by=? WHERE id=(SELECT v.id FROM $tableFull WHERE pa.group_id=? AND v.id=?)"
        check(q)
      }
      it("should build selectOneFull") {
        val q = VenueRepoSql.selectOneFull(group.id, venue.id)
        q.sql shouldBe s"SELECT $fieldsFull FROM $tableFull WHERE pa.group_id=? AND v.id=?"
        check(q)
      }
      it("should build selectPageFull") {
        val q = VenueRepoSql.selectPageFull(group.id, params)
        check(q, s"SELECT $fieldsFull FROM $tableFull WHERE pa.group_id=? ORDER BY v.created IS NULL, v.created OFFSET 0 LIMIT 20")
      }
      it("should build selectAllFull for group id") {
        val q = VenueRepoSql.selectAllFull(group.id)
        q.sql shouldBe s"SELECT $fieldsFull FROM $tableFull WHERE pa.group_id=? "
        check(q)
      }
      it("should build selectAllFull for partner id") {
        val q = VenueRepoSql.selectAllFull(partner.id)
        q.sql shouldBe s"SELECT $fieldsFull FROM $tableFull WHERE v.partner_id=? "
        check(q)
      }
      it("should build selectAllFull for group id and ids") {
        val q = VenueRepoSql.selectAllFull(group.id, NonEmptyList.of(venue.id))
        q.sql shouldBe s"SELECT $fieldsFull FROM $tableFull WHERE pa.group_id=? AND  v.id IN (?) "
        check(q)
      }
    }
  }
}

object VenueRepoSqlSpec {

  import RepoSpec._

  val table = "venues"
  val fieldsInsert = "id, partner_id, contact_id, address, address_lat, address_lng, address_country, description, room_size, meetupGroup, meetupVenue, created, created_by, updated, updated_by"
  val fields = "id, partner_id, contact_id, address, description, room_size, meetupGroup, meetupVenue, created, created_by, updated, updated_by"

  private val tableFull = s"$table v INNER JOIN $partnerTable ON v.partner_id=pa.id LEFT OUTER JOIN $contactTable ON v.contact_id=ct.id"
  private val fieldsFull = s"${mapFields(fields, "v." + _)}, $partnerFields, $contactFields"
}
