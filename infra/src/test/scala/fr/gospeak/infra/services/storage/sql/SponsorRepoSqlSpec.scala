package fr.gospeak.infra.services.storage.sql

import fr.gospeak.infra.services.storage.sql.SponsorRepoSql._
import fr.gospeak.infra.services.storage.sql.testingutils.RepoSpec

class SponsorRepoSqlSpec extends RepoSpec {
  private val fields = "id, group_id, partner_id, sponsor_pack_id, start, finish, paid, price, currency, created, created_by, updated, updated_by"

  describe("SponsorRepoSql") {
    describe("Queries") {
      it("should build insert") {
        val q = insert(sponsor)
        q.sql shouldBe s"INSERT INTO sponsors ($fields) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)"
        check(q)
      }
      it("should build update") {
        val q = update(group.id, sponsor.id)(sponsor.data, user.id, now)
        q.sql shouldBe "UPDATE sponsors SET partner_id=?, sponsor_pack_id=?, start=?, finish=?, paid=?, price=?, currency=?, updated=?, updated_by=? WHERE group_id=? AND id=?"
        check(q)
      }
      it("should build selectOne") {
        val q = selectOne(group.id, sponsor.id)
        q.sql shouldBe s"SELECT $fields FROM sponsors WHERE group_id=? AND id=?"
        check(q)
      }
      it("should build selectPage") {
        val (s, c) = selectPage(group.id, params)
        s.sql shouldBe s"SELECT $fields FROM sponsors WHERE group_id=? ORDER BY start IS NULL, start DESC OFFSET 0 LIMIT 20"
        c.sql shouldBe "SELECT count(*) FROM sponsors WHERE group_id=? "
        check(s)
        check(c)
      }
    }
  }
}
