package fr.gospeak.infra.services.storage.sql

import fr.gospeak.infra.services.storage.sql.PartnerRepoSqlSpec.{fields => partnerFields, table => partnerTable}
import fr.gospeak.infra.services.storage.sql.SponsorPackRepoSqlSpec.{fields => sponsorPackFields, table => sponsorPackTable}
import fr.gospeak.infra.services.storage.sql.ContactRepoSqlSpec.{fields => contactFields, table => contactTable}
import fr.gospeak.infra.services.storage.sql.SponsorRepoSqlSpec._
import fr.gospeak.infra.services.storage.sql.testingutils.RepoSpec

class SponsorRepoSqlSpec extends RepoSpec {
  describe("SponsorRepoSql") {
    describe("Queries") {
      it("should build insert") {
        val q = SponsorRepoSql.insert(sponsor)
        q.sql shouldBe s"INSERT INTO $table ($fields) VALUES (${mapFields(fields, _ => "?")})"
        check(q)
      }
      it("should build update") {
        val q = SponsorRepoSql.update(group.id, sponsor.id)(sponsor.data, user.id, now)
        q.sql shouldBe s"UPDATE $table SET partner_id=?, sponsor_pack_id=?, contact_id=?, start=?, finish=?, paid=?, price=?, currency=?, updated=?, updated_by=? WHERE group_id=? AND id=?"
        check(q)
      }
      it("should build selectOne") {
        val q = SponsorRepoSql.selectOne(group.id, sponsor.id)
        q.sql shouldBe s"SELECT $fields FROM $table WHERE group_id=? AND id=?"
        check(q)
      }
      it("should build selectPage") {
        val (s, c) = SponsorRepoSql.selectPage(group.id, params)
        s.sql shouldBe s"SELECT $fields FROM $table WHERE group_id=? ORDER BY start IS NULL, start DESC OFFSET 0 LIMIT 20"
        c.sql shouldBe s"SELECT count(*) FROM $table WHERE group_id=? "
        check(s)
        check(c)
      }
      it("should build selectCurrent") {
        val q = SponsorRepoSql.selectCurrent(group.id, now)
        q.sql shouldBe s"SELECT $fieldsFull FROM $tableFull WHERE s.group_id=? AND s.start < ? AND s.finish > ?"
        // check(q)
      }
      it("should build selectAll group") {
        val q = SponsorRepoSql.selectAll(group.id)
        q.sql shouldBe s"SELECT $fields FROM $table WHERE group_id=?"
        check(q)
      }
      it("should build selectAllFull partner") {
        val q = SponsorRepoSql.selectAllFull(group.id, partner.id)
        q.sql shouldBe s"SELECT $fieldsFull FROM $tableFull WHERE s.group_id=? AND s.partner_id=? ORDER BY s.start IS NULL, s.start DESC "
        check(q)
      }
    }
  }
}

object SponsorRepoSqlSpec {

  import RepoSpec._

  val table = "sponsors"
  val fields = "id, group_id, partner_id, sponsor_pack_id, contact_id, start, finish, paid, price, currency, created, created_by, updated, updated_by"

  private val tableFull = s"$table s INNER JOIN $sponsorPackTable sp ON s.sponsor_pack_id=sp.id INNER JOIN $partnerTable p ON s.partner_id=p.id LEFT OUTER JOIN $contactTable c ON s.contact_id=c.id"
  private val fieldsFull = s"${mapFields(fields, "s." + _)}, ${mapFields(sponsorPackFields, "sp." + _)}, ${mapFields(partnerFields, "p." + _)}, ${mapFields(contactFields, "c." + _)}"
}
