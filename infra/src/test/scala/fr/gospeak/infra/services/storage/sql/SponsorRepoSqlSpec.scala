package fr.gospeak.infra.services.storage.sql

import fr.gospeak.infra.services.storage.sql.SponsorRepoSql._
import fr.gospeak.infra.services.storage.sql.SponsorRepoSqlSpec._
import fr.gospeak.infra.services.storage.sql.testingutils.RepoSpec

class SponsorRepoSqlSpec extends RepoSpec {
  describe("SponsorRepoSql") {
    describe("Queries") {
      it("should build insert") {
        val q = insert(sponsor)
        q.sql shouldBe s"INSERT INTO sponsors ($fieldList) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)"
        check(q)
      }
      it("should build update") {
        val q = update(group.id, sponsor.id)(sponsor.data, user.id, now)
        q.sql shouldBe "UPDATE sponsors SET partner_id=?, sponsor_pack_id=?, start=?, finish=?, paid=?, price=?, currency=?, updated=?, updated_by=? WHERE group_id=? AND id=?"
        check(q)
      }
      it("should build selectOne") {
        val q = selectOne(group.id, sponsor.id)
        q.sql shouldBe s"SELECT $fieldList FROM sponsors WHERE group_id=? AND id=?"
        check(q)
      }
      it("should build selectPage") {
        val (s, c) = selectPage(group.id, params)
        s.sql shouldBe s"SELECT $fieldList FROM sponsors WHERE group_id=? ORDER BY start IS NULL, start DESC OFFSET 0 LIMIT 20"
        c.sql shouldBe "SELECT count(*) FROM sponsors WHERE group_id=? "
        check(s)
        check(c)
      }
      it("should build selectCurrent") {
        val q = selectCurrent(group.id, now)
        q.sql shouldBe s"SELECT $sponsorPartnerSponsorPackFields FROM $sponsorPartnerSponsorPackTables WHERE s.group_id=? AND s.start < ? AND s.finish > ?"
        // check(q)
      }
      it("should build selectAll group") {
        val q = selectAll(group.id)
        q.sql shouldBe s"SELECT $fieldList FROM sponsors WHERE group_id=?"
        check(q)
      }
      it("should build selectAll partner") {
        val q = selectAll(group.id, partner.id)
        q.sql shouldBe s"SELECT $fieldList FROM sponsors WHERE group_id=? AND partner_id=?"
        check(q)
      }
    }
  }
}

object SponsorRepoSqlSpec {

  import RepoSpec._

  val fieldList = "id, group_id, partner_id, sponsor_pack_id, start, finish, paid, price, currency, created, created_by, updated, updated_by"

  private val sponsorPartnerSponsorPackTables = "sponsors s INNER JOIN partners p ON s.partner_id=p.id INNER JOIN sponsor_packs sp ON s.sponsor_pack_id=sp.id"
  private val sponsorPartnerSponsorPackFields = s"${withPrefix(fieldList, "s.")}, ${withPrefix(PartnerRepoSqlSpec.fieldList, "p.")}, ${withPrefix(SponsorPackRepoSqlSpec.fieldList, "sp.")}"
}
