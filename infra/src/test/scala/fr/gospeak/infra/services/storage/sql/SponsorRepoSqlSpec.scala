package fr.gospeak.infra.services.storage.sql

import fr.gospeak.infra.services.storage.sql.ContactRepoSqlSpec.{fields => contactFields, table => contactTable}
import fr.gospeak.infra.services.storage.sql.PartnerRepoSqlSpec.{fields => partnerFields, table => partnerTable}
import fr.gospeak.infra.services.storage.sql.SponsorPackRepoSqlSpec.{fields => sponsorPackFields, table => sponsorPackTable}
import fr.gospeak.infra.services.storage.sql.SponsorRepoSqlSpec._
import fr.gospeak.infra.services.storage.sql.testingutils.RepoSpec

class SponsorRepoSqlSpec extends RepoSpec {
  describe("SponsorRepoSql") {
    describe("Queries") {
      it("should build insert") {
        val q = SponsorRepoSql.insert(sponsor)
        check(q, s"INSERT INTO ${table.stripSuffix(" s")} (${mapFields(fields, _.stripPrefix("s."))}) VALUES (${mapFields(fields, _ => "?")})")
      }
      it("should build update") {
        val q = SponsorRepoSql.update(group.id, sponsor.id)(sponsor.data, user.id, now)
        check(q, s"UPDATE $table SET s.partner_id=?, s.sponsor_pack_id=?, s.contact_id=?, s.start=?, s.finish=?, s.paid=?, s.price=?, s.currency=?, s.updated=?, s.updated_by=? WHERE s.group_id=? AND s.id=?")
      }
      it("should build delete") {
        val q = SponsorRepoSql.delete(group.id, sponsor.id)
        check(q, s"DELETE FROM $table WHERE s.group_id=? AND s.id=?")
      }
      it("should build selectOne") {
        val q = SponsorRepoSql.selectOne(group.id, sponsor.id)
        check(q, s"SELECT $fields FROM $table WHERE s.group_id=? AND s.id=?")
      }
      it("should build selectPage") {
        val q = SponsorRepoSql.selectPage(group.id, params)
        check(q, s"SELECT $fieldsFull FROM $tableFull WHERE s.group_id=? ORDER BY s.start IS NULL, s.start DESC OFFSET 0 LIMIT 20")
      }
      it("should build selectCurrent") {
        val q = SponsorRepoSql.selectCurrent(group.id, now)
        q.fr.query.sql shouldBe s"SELECT $fieldsFull FROM $tableFull WHERE s.group_id=? AND s.start < ? AND s.finish > ?"
        // check(q, s"SELECT $fieldsFull FROM $tableFull WHERE s.group_id=? AND s.start < ? AND s.finish > ?")
      }
      it("should build selectAll group") {
        val q = SponsorRepoSql.selectAll(group.id)
        check(q, s"SELECT $fields FROM $table WHERE s.group_id=?")
      }
      it("should build selectAllFull partner") {
        val q = SponsorRepoSql.selectAllFull(group.id, partner.id)
        check(q, s"SELECT $fieldsFull FROM $tableFull WHERE s.group_id=? AND s.partner_id=? ORDER BY s.start IS NULL, s.start DESC ")
      }
    }
  }
}

object SponsorRepoSqlSpec {

  import RepoSpec._

  val table = "sponsors s"
  val fields: String = mapFields("id, group_id, partner_id, sponsor_pack_id, contact_id, start, finish, paid, price, currency, created, created_by, updated, updated_by", "s." + _)

  private val tableFull = s"$table INNER JOIN $sponsorPackTable ON s.sponsor_pack_id=sp.id INNER JOIN $partnerTable ON s.partner_id=pa.id LEFT OUTER JOIN $contactTable ON s.contact_id=ct.id"
  private val fieldsFull = s"$fields, $sponsorPackFields, $partnerFields, $contactFields"
}
