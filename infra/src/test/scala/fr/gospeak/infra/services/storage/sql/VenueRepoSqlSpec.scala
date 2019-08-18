package fr.gospeak.infra.services.storage.sql

import cats.data.NonEmptyList
import fr.gospeak.infra.services.storage.sql.VenueRepoSql._
import fr.gospeak.infra.services.storage.sql.testingutils.RepoSpec

class VenueRepoSqlSpec extends RepoSpec {
  private val fields = "id, partner_id, address, address_lat, address_lng, address_country, description, room_size, meetupGroup, meetupVenue, created, created_by, updated, updated_by"
  private val fieldsSelect = "id, partner_id, address, description, room_size, meetupGroup, meetupVenue, created, created_by, updated, updated_by"

  describe("VenueRepoSql") {
    describe("Queries") {
      it("should build insert") {
        val q = insert(venue)
        q.sql shouldBe s"INSERT INTO venues ($fields) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)"
        check(q)
      }
      it("should build update") {
        val q = update(group.id, venue.id)(venue.data, user.id, now)
        q.sql shouldBe "UPDATE venues SET address=?, address_lat=?, address_lng=?, address_country=?, description=?, room_size=?, updated=?, updated_by=? WHERE id=(SELECT v.id FROM venues v INNER JOIN partners p ON v.partner_id=p.id WHERE p.group_id=? AND v.id=?)"
        check(q)
      }
      it("should build selectPage") {
        val (s, c) = selectPage(group.id, params)
        s.sql shouldBe s"SELECT ${fieldsPrefixedBy(PartnerRepoSqlSpec.fields, "p.")}, ${fieldsPrefixedBy(fieldsSelect, "v.")} FROM venues v INNER JOIN partners p ON v.partner_id=p.id WHERE p.group_id=? ORDER BY v.created IS NULL, v.created OFFSET 0 LIMIT 20"
        c.sql shouldBe "SELECT count(*) FROM venues v INNER JOIN partners p ON v.partner_id=p.id WHERE p.group_id=? "
        check(s)
        check(c)
      }
      it("should build selectAll for group id") {
        val q = selectAll(group.id)
        q.sql shouldBe s"SELECT ${fieldsPrefixedBy(PartnerRepoSqlSpec.fields, "p.")}, ${fieldsPrefixedBy(fieldsSelect, "v.")} FROM venues v INNER JOIN partners p ON v.partner_id=p.id WHERE p.group_id=? "
        check(q)
      }
      it("should build selectAll for partner id") {
        val q = selectAll(partner.id)
        q.sql shouldBe s"SELECT $fieldsSelect FROM venues WHERE partner_id=? "
        check(q)
      }
      it("should build selectAll for group id and ids") {
        val q = selectAll(group.id, NonEmptyList.of(venue.id))
        q.sql shouldBe s"SELECT ${fieldsPrefixedBy(PartnerRepoSqlSpec.fields, "p.")}, ${fieldsPrefixedBy(fieldsSelect, "v.")} FROM venues v INNER JOIN partners p ON v.partner_id=p.id WHERE p.group_id=? AND  v.id IN (?) "
        check(q)
      }
      it("should build selectOne") {
        val q = selectOne(group.id, venue.id)
        q.sql shouldBe s"SELECT ${fieldsPrefixedBy(PartnerRepoSqlSpec.fields, "p.")}, ${fieldsPrefixedBy(fieldsSelect, "v.")} FROM venues v INNER JOIN partners p ON v.partner_id=p.id WHERE p.group_id=? AND v.id=?"
        check(q)
      }
    }
  }
}
