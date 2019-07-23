package fr.gospeak.infra.services.storage.sql

import fr.gospeak.infra.services.storage.sql.testingutils.RepoSpec
import SponsorPackRepoSql._

class SponsorPackRepoSqlSpec extends RepoSpec {
  private val fields = "id, group_id, slug, name, description, price, currency, duration, active, created, created_by, updated, updated_by"

  describe("SponsorPackRepoSql") {
    describe("Queries") {
      it("should build insert") {
        val q = insert(sponsorPack)
        q.sql shouldBe s"INSERT INTO sponsor_packs ($fields) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)"
        check(q)
      }
      it("should build update") {
        val q = update(group.id, sponsorPack.slug)(sponsorPack.data, user.id, now)
        q.sql shouldBe "UPDATE sponsor_packs SET slug=?, name=?, description=?, price=?, currency=?, duration=?, updated=?, updated_by=? WHERE group_id=? AND slug=?"
        check(q)
      }
      it("should build setActive") {
        val q = setActive(group.id, sponsorPack.slug)(active = true, user.id, now)
        q.sql shouldBe "UPDATE sponsor_packs SET active=?, updated=?, updated_by=? WHERE group_id=? AND slug=?"
        check(q)
      }
      it("should build selectOne") {
        val q = selectOne(group.id, sponsorPack.slug)
        q.sql shouldBe s"SELECT $fields FROM sponsor_packs WHERE group_id=? AND slug=?"
        check(q)
      }
      it("should build selectAll") {
        val q = selectAll(group.id)
        q.sql shouldBe s"SELECT $fields FROM sponsor_packs WHERE group_id=?"
        check(q)
      }
      it("should build selectActives") {
        val q = selectActives(group.id)
        q.sql shouldBe s"SELECT $fields FROM sponsor_packs WHERE group_id=? AND active=?"
        check(q)
      }
    }
  }
}
