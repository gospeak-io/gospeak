package fr.gospeak.infra.services.storage.sql

import cats.data.NonEmptyList
import fr.gospeak.infra.services.storage.sql.PartnerRepoSqlSpec._
import fr.gospeak.infra.services.storage.sql.testingutils.RepoSpec

class PartnerRepoSqlSpec extends RepoSpec {
  describe("PartnerRepoSql") {
    describe("Queries") {
      it("should build insert") {
        val q = PartnerRepoSql.insert(partner)
        q.sql shouldBe s"INSERT INTO $table ($fields) VALUES (${mapFields(fields, _ => "?")})"
        check(q)
      }
      it("should build update") {
        val q = PartnerRepoSql.update(group.id, partner.slug)(partner.data, user.id, now)
        q.sql shouldBe s"UPDATE $table SET slug=?, name=?, notes=?, description=?, logo=?, twitter=?, updated=?, updated_by=? WHERE group_id=? AND slug=?"
        check(q)
      }
      it("should build selectPage") {
        val q = PartnerRepoSql.selectPage(group.id, params)
        q.query.sql shouldBe s"SELECT $fields FROM $table WHERE group_id=? ORDER BY name IS NULL, name OFFSET 0 LIMIT 20"
        q.count.sql shouldBe s"SELECT count(*) FROM $table WHERE group_id=? "
        check(q.query)
        check(q.count)
      }
      it("should build selectAll") {
        val q = PartnerRepoSql.selectAll(group.id)
        q.sql shouldBe s"SELECT $fields FROM $table WHERE group_id=? "
        check(q)
      }
      it("should build selectAll with partner ids") {
        val q = PartnerRepoSql.selectAll(NonEmptyList.of(partner.id))
        q.sql shouldBe s"SELECT $fields FROM $table WHERE id IN (?) "
        check(q)
      }
      it("should build selectOne") {
        val q = PartnerRepoSql.selectOne(group.id, partner.slug)
        q.sql shouldBe s"SELECT $fields FROM $table WHERE group_id=? AND slug=?"
        check(q)
      }
    }
  }
}

object PartnerRepoSqlSpec {
  val table = "partners"
  val fields = "id, group_id, slug, name, notes, description, logo, twitter, created, created_by, updated, updated_by"
}
