package fr.gospeak.infra.services.storage.sql

import cats.data.NonEmptyList
import fr.gospeak.infra.services.storage.sql.SponsorPackRepoSqlSpec._
import fr.gospeak.infra.services.storage.sql.testingutils.RepoSpec
import fr.gospeak.infra.services.storage.sql.testingutils.RepoSpec.mapFields

class SponsorPackRepoSqlSpec extends RepoSpec {
  describe("SponsorPackRepoSql") {
    describe("Queries") {
      it("should build insert") {
        val q = SponsorPackRepoSql.insert(sponsorPack)
        check(q, s"INSERT INTO ${table.stripSuffix(" sp")} (${mapFields(fields, _.stripPrefix("sp."))}) VALUES (${mapFields(fields, _ => "?")})")
      }
      it("should build update") {
        val q = SponsorPackRepoSql.update(group.id, sponsorPack.slug)(sponsorPack.data, user.id, now)
        check(q, s"UPDATE $table SET slug=?, name=?, description=?, price=?, currency=?, duration=?, updated_at=?, updated_by=? WHERE sp.group_id=? AND sp.slug=?")
      }
      it("should build setActive") {
        val q = SponsorPackRepoSql.setActive(group.id, sponsorPack.slug)(active = true, user.id, now)
        check(q, s"UPDATE $table SET active=?, updated_at=?, updated_by=? WHERE sp.group_id=? AND sp.slug=?")
      }
      it("should build selectOne") {
        val q = SponsorPackRepoSql.selectOne(group.id, sponsorPack.slug)
        check(q, s"SELECT $fields FROM $table WHERE sp.group_id=? AND sp.slug=? $orderBy")
      }
      it("should build selectAll ids") {
        val q = SponsorPackRepoSql.selectAll(NonEmptyList.of(sponsorPack.id, sponsorPack.id))
        check(q, s"SELECT $fields FROM $table WHERE sp.id IN (?, ?)  $orderBy")
      }
      it("should build selectAll") {
        val q = SponsorPackRepoSql.selectAll(group.id)
        check(q, s"SELECT $fields FROM $table WHERE sp.group_id=? $orderBy")
      }
      it("should build selectActives") {
        val q = SponsorPackRepoSql.selectActives(group.id)
        check(q, s"SELECT $fields FROM $table WHERE sp.group_id=? AND sp.active=? $orderBy")
      }
    }
  }
}

object SponsorPackRepoSqlSpec {
  val table = "sponsor_packs sp"
  val fields: String = mapFields("id, group_id, slug, name, description, price, currency, duration, active, created_at, created_by, updated_at, updated_by", "sp." + _)
  val orderBy = "ORDER BY sp.active IS NULL, sp.active DESC, sp.price IS NULL, sp.price DESC"
}
