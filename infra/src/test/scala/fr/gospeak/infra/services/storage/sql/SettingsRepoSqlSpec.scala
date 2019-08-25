package fr.gospeak.infra.services.storage.sql

import fr.gospeak.infra.services.storage.sql.SettingsRepoSql._
import fr.gospeak.infra.services.storage.sql.SettingsRepoSqlSpec._
import fr.gospeak.infra.services.storage.sql.testingutils.RepoSpec

class SettingsRepoSqlSpec extends RepoSpec {
  describe("SettingsRepoSql") {
    describe("Queries") {
      it("should build insert group settings") {
        val q = insert(group.id, "", user.id, now)
        q.sql shouldBe s"INSERT INTO settings ($fieldList) VALUES (?, ?, ?, ?, ?)"
        check(q)
      }
      it("should build update group settings") {
        val q = update(group.id, "", user.id, now)
        q.sql shouldBe "UPDATE settings SET value=?, updated=?, updated_by=? WHERE target=? AND target_id=?"
        check(q)
      }
      it("should build selectOne group settings") {
        val q = selectOne(group.id)
        q.sql shouldBe s"SELECT value FROM settings WHERE target=? AND target_id=?"
        check(q)
      }
    }
  }
}

object SettingsRepoSqlSpec {
  val fieldList = "target, target_id, value, updated, updated_by"
}
