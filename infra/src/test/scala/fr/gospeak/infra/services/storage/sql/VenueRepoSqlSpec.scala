package fr.gospeak.infra.services.storage.sql

import cats.data.NonEmptyList
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
        q.sql shouldBe s"UPDATE $table SET address=?, address_lat=?, address_lng=?, address_country=?, description=?, room_size=?, meetupGroup=?, meetupVenue=?, updated=?, updated_by=? WHERE id=(SELECT v.id FROM $tableFull WHERE p.group_id=? AND v.id=?)"
        check(q)
      }
      it("should build selectPage") {
        val (s, c) = VenueRepoSql.selectPage(group.id, params)
        s.sql shouldBe s"SELECT $fieldsFull FROM $tableFull WHERE p.group_id=? ORDER BY v.created IS NULL, v.created OFFSET 0 LIMIT 20"
        c.sql shouldBe s"SELECT count(*) FROM $tableFull WHERE p.group_id=? "
        check(s)
        check(c)
      }
      it("should build selectAll for group id") {
        val q = VenueRepoSql.selectAll(group.id)
        q.sql shouldBe s"SELECT $fieldsFull FROM $tableFull WHERE p.group_id=? "
        check(q)
      }
      it("should build selectAll for partner id") {
        val q = VenueRepoSql.selectAll(partner.id)
        q.sql shouldBe s"SELECT $fields FROM $table WHERE partner_id=? "
        check(q)
      }
      it("should build selectAll for group id and ids") {
        val q = VenueRepoSql.selectAll(group.id, NonEmptyList.of(venue.id))
        q.sql shouldBe s"SELECT $fieldsFull FROM $tableFull WHERE p.group_id=? AND  v.id IN (?) "
        check(q)
      }
      it("should build selectOne") {
        val q = VenueRepoSql.selectOne(group.id, venue.id)
        q.sql shouldBe s"SELECT $fieldsFull FROM $tableFull WHERE p.group_id=? AND v.id=?"
        check(q)
      }
    }
  }
}

object VenueRepoSqlSpec {

  import RepoSpec._

  val table = "venues"
  val fieldsInsert = "id, partner_id, address, address_lat, address_lng, address_country, description, room_size, meetupGroup, meetupVenue, created, created_by, updated, updated_by"
  val fields = "id, partner_id, address, description, room_size, meetupGroup, meetupVenue, created, created_by, updated, updated_by"

  private val tableFull = s"$table v INNER JOIN $partnerTable p ON v.partner_id=p.id"
  private val fieldsFull = s"${mapFields(fields, "v." + _)}, ${mapFields(partnerFields, "p." + _)}"
}
