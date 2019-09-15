package fr.gospeak.infra.services.storage.sql

import cats.data.NonEmptyList
import fr.gospeak.infra.services.storage.sql.SponsorPackRepoSqlSpec._
import fr.gospeak.infra.services.storage.sql.testingutils.RepoSpec

class SponsorPackRepoSqlSpec extends RepoSpec {
  describe("SponsorPackRepoSql") {
    describe("Queries") {
      it("should build insert") {
        val q = SponsorPackRepoSql.insert(sponsorPack)
        q.sql shouldBe s"INSERT INTO $table ($fields) VALUES (${mapFields(fields, _ => "?")})"
        check(q)
      }
      it("should build update") {
        val q = SponsorPackRepoSql.update(group.id, sponsorPack.slug)(sponsorPack.data, user.id, now)
        q.sql shouldBe s"UPDATE $table SET slug=?, name=?, description=?, price=?, currency=?, duration=?, updated=?, updated_by=? WHERE group_id=? AND slug=?"
        check(q)
      }
      it("should build setActive") {
        val q = SponsorPackRepoSql.setActive(group.id, sponsorPack.slug)(active = true, user.id, now)
        q.sql shouldBe s"UPDATE $table SET active=?, updated=?, updated_by=? WHERE group_id=? AND slug=?"
        check(q)
      }
      it("should build selectOne") {
        val q = SponsorPackRepoSql.selectOne(group.id, sponsorPack.slug)
        q.sql shouldBe s"SELECT $fields FROM $table WHERE group_id=? AND slug=?"
        check(q)
      }
      it("should build selectAll ids") {
        val q = SponsorPackRepoSql.selectAll(NonEmptyList.of(sponsorPack.id, sponsorPack.id))
        q.sql shouldBe s"SELECT $fields FROM $table WHERE id IN (?, ?) "
        check(q)
      }
      it("should build selectAll") {
        val q = SponsorPackRepoSql.selectAll(group.id)
        q.sql shouldBe s"SELECT $fields FROM $table WHERE group_id=?"
        check(q)
      }
      it("should build selectActives") {
        val q = SponsorPackRepoSql.selectActives(group.id)
        q.sql shouldBe s"SELECT $fields FROM $table WHERE group_id=? AND active=?"
        check(q)
      }
    }
  }
}

object SponsorPackRepoSqlSpec {
  val table = "sponsor_packs"
  val fields = "id, group_id, slug, name, description, price, currency, duration, active, created, created_by, updated, updated_by"
}
