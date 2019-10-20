package fr.gospeak.infra.services.storage.sql

import cats.data.NonEmptyList
import fr.gospeak.infra.services.storage.sql.PartnerRepoSqlSpec._
import fr.gospeak.infra.services.storage.sql.testingutils.RepoSpec
import fr.gospeak.infra.services.storage.sql.testingutils.RepoSpec.mapFields

class PartnerRepoSqlSpec extends RepoSpec {
  describe("PartnerRepoSql") {
    describe("Queries") {
      it("should build insert") {
        val q = PartnerRepoSql.insert(partner)
        check(q, s"INSERT INTO ${table.stripSuffix(" pa")} (${mapFields(fields, _.stripPrefix("pa."))}) VALUES (${mapFields(fields, _ => "?")})")
      }
      it("should build update") {
        val q = PartnerRepoSql.update(group.id, partner.slug)(partner.data, user.id, now)
        check(q, s"UPDATE $table SET slug=?, name=?, notes=?, description=?, logo=?, twitter=?, updated=?, updated_by=? WHERE pa.group_id=? AND pa.slug=?")
      }
      it("should build selectPage") {
        val q = PartnerRepoSql.selectPage(group.id, params)
        check(q, s"SELECT $fields FROM $table WHERE pa.group_id=? $orderBy OFFSET 0 LIMIT 20")
      }
      it("should build selectAll") {
        val q = PartnerRepoSql.selectAll(group.id)
        check(q, s"SELECT $fields FROM $table WHERE pa.group_id=? $orderBy")
      }
      it("should build selectAll with partner ids") {
        val q = PartnerRepoSql.selectAll(NonEmptyList.of(partner.id))
        check(q, s"SELECT $fields FROM $table WHERE pa.id IN (?)  $orderBy")
      }
      it("should build selectOne") {
        val q = PartnerRepoSql.selectOne(group.id, partner.slug)
        check(q, s"SELECT $fields FROM $table WHERE pa.group_id=? AND pa.slug=? $orderBy")
      }
    }
  }
}

object PartnerRepoSqlSpec {
  val table = "partners pa"
  val fields: String = mapFields("id, group_id, slug, name, notes, description, logo, twitter, created, created_by, updated, updated_by", "pa." + _)
  val orderBy = "ORDER BY pa.name IS NULL, pa.name"
}
