package fr.gospeak.infra.services.storage.sql

import fr.gospeak.infra.services.storage.sql.GroupSettingsRepoSqlSpec._
import fr.gospeak.infra.services.storage.sql.testingutils.RepoSpec

class GroupSettingsRepoSqlSpec extends RepoSpec {
  describe("GroupSettingsRepoSql") {
    describe("Queries") {
      it("should build insert group settings") {
        val q = GroupSettingsRepoSql.insert(group.id, groupSettings, user.id, now)
        q.sql shouldBe s"INSERT INTO $table ($fields) VALUES (${mapFields(fields, _ => "?")})"
        check(q)
      }
      it("should build update group settings") {
        val q = GroupSettingsRepoSql.update(group.id, groupSettings, user.id, now)
        q.sql shouldBe s"UPDATE $table SET ${fields.split(", ").drop(1).map(_ + "=?").mkString(", ")} WHERE group_id=?"
        check(q)
      }
      it("should build selectOne group settings") {
        val q = GroupSettingsRepoSql.selectOne(group.id)
        q.sql shouldBe s"SELECT $fieldsSelect FROM $table WHERE group_id=?"
        check(q)
      }
      it("should build selectOneAccounts group settings") {
        val q = GroupSettingsRepoSql.selectOneAccounts(group.id)
        q.sql shouldBe s"SELECT $meetupFields, $slackFields FROM $table WHERE group_id=?"
        check(q)
      }
      it("should build selectOneMeetup group settings") {
        val q = GroupSettingsRepoSql.selectOneMeetup(group.id)
        q.sql shouldBe s"SELECT $meetupFields FROM $table WHERE group_id=?"
        check(q)
      }
      it("should build selectOneSlack group settings") {
        val q = GroupSettingsRepoSql.selectOneSlack(group.id)
        q.sql shouldBe s"SELECT $slackFields FROM $table WHERE group_id=?"
        check(q)
      }
      it("should build selectOneEventDescription group settings") {
        val q = GroupSettingsRepoSql.selectOneEventDescription(group.id)
        q.sql shouldBe s"SELECT event_description FROM $table WHERE group_id=?"
        check(q)
      }
      it("should build selectOneEventTemplates group settings") {
        val q = GroupSettingsRepoSql.selectOneEventTemplates(group.id)
        q.sql shouldBe s"SELECT event_templates FROM $table WHERE group_id=?"
        check(q)
      }
      it("should build selectOneActions group settings") {
        val q = GroupSettingsRepoSql.selectOneActions(group.id)
        q.sql shouldBe s"SELECT actions FROM $table WHERE group_id=?"
        check(q)
      }
    }
  }
}

object GroupSettingsRepoSqlSpec {
  val meetupFields = "meetup_access_token, meetup_refresh_token, meetup_group_slug, meetup_logged_user_id, meetup_logged_user_name"
  val slackFields = "slack_token, slack_bot_name, slack_bot_avatar"
  val eventFields = "event_description, event_templates"

  val table = "group_settings"
  val fields = s"group_id, $meetupFields, $slackFields, $eventFields, actions, updated, updated_by"
  val fieldsSelect = s"$meetupFields, $slackFields, $eventFields, actions"
}
