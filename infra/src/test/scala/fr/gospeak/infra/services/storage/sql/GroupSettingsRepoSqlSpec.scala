package fr.gospeak.infra.services.storage.sql

import fr.gospeak.infra.services.storage.sql.GroupSettingsRepoSql._
import fr.gospeak.infra.services.storage.sql.GroupSettingsRepoSqlSpec._
import fr.gospeak.infra.services.storage.sql.testingutils.RepoSpec

class GroupSettingsRepoSqlSpec extends RepoSpec {
  describe("GroupSettingsRepoSql") {
    describe("Queries") {
      it("should build insert group settings") {
        val q = insert(group.id, groupSettings, user.id, now)
        q.sql shouldBe s"INSERT INTO group_settings ($fieldList) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)"
        check(q)
      }
      it("should build update group settings") {
        val q = update(group.id, groupSettings, user.id, now)
        q.sql shouldBe s"UPDATE group_settings SET ${fieldList.split(", ").drop(1).map(_ + "=?").mkString(", ")} WHERE group_id=?"
        check(q)
      }
      it("should build selectOne group settings") {
        val q = selectOne(group.id)
        q.sql shouldBe s"SELECT $fieldListSelect FROM group_settings WHERE group_id=?"
        check(q)
      }
    }
  }
}

object GroupSettingsRepoSqlSpec {
  val fieldList = "group_id, meetup_access_token, meetup_refresh_token, meetup_group_slug, meetup_logged_user_id, meetup_logged_user_name, slack_token, slack_bot_name, slack_bot_avatar, event_default_description, event_templates, actions, updated, updated_by"
  val fieldListSelect: String = fieldList.split(", ").drop(1).dropRight(2).mkString(", ")
}
