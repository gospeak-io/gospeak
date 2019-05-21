package fr.gospeak.infra.services.storage.sql

import fr.gospeak.infra.services.storage.sql.PartnerRepoSql._
import fr.gospeak.infra.services.storage.sql.testingutils.RepoSpec

class PartnerRepoSqlSpec extends RepoSpec {
  private val fields = "id, group_id, slug, name, description, logo, created, created_by, updated, updated_by"

  describe("PartnerRepoSql") {
    describe("Queries") {
      it("should build insert") {
        val q = insert(partner)
        q.sql shouldBe s"INSERT INTO partners ($fields) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)"
        check(q)
      }
      it("should build update") {
        val q = update(group.id, partner.slug)(partner.data, user.id, now)
        q.sql shouldBe "UPDATE partners SET slug=?, name=?, description=?, logo=?, updated=?, updated_by=? WHERE group_id=? AND slug=?"
        check(q)
      }
      it("should build selectPage") {
        val (s, c) = selectPage(group.id, params)
        s.sql shouldBe s"SELECT $fields FROM partners WHERE group_id=? ORDER BY name IS NULL, name OFFSET 0 LIMIT 20"
        c.sql shouldBe "SELECT count(*) FROM partners WHERE group_id=? "
        check(s)
        check(c)
      }
      it("should build selectAll") {
        val q = selectAll(group.id)
        q.sql shouldBe s"SELECT $fields FROM partners WHERE group_id=? "
        check(q)
      }
      it("should build selectOne") {
        val q = selectOne(group.id, partner.slug)
        q.sql shouldBe s"SELECT $fields FROM partners WHERE group_id=? AND slug=?"
        check(q)
      }
    }
  }
}

object PartnerRepoSqlSpec {
  val fields = "id, group_id, slug, name, description, logo, created, created_by, updated, updated_by"
}
