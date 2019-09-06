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
      it("should build selectOneAccounts group settings") {
        val q = selectOneAccounts(group.id)
        q.sql shouldBe s"SELECT $meetupFields, $slackFields FROM group_settings WHERE group_id=?"
        check(q)
      }
      it("should build selectOneMeetup group settings") {
        val q = selectOneMeetup(group.id)
        q.sql shouldBe s"SELECT $meetupFields FROM group_settings WHERE group_id=?"
        check(q)
      }
      it("should build selectOneSlack group settings") {
        val q = selectOneSlack(group.id)
        q.sql shouldBe s"SELECT $slackFields FROM group_settings WHERE group_id=?"
        check(q)
      }
      it("should build selectOneEventDescription group settings") {
        val q = selectOneEventDescription(group.id)
        q.sql shouldBe s"SELECT event_default_description FROM group_settings WHERE group_id=?"
        check(q)
      }
      it("should build selectOneEventTemplates group settings") {
        val q = selectOneEventTemplates(group.id)
        q.sql shouldBe s"SELECT event_templates FROM group_settings WHERE group_id=?"
        check(q)
      }
      it("should build selectOneActions group settings") {
        val q = selectOneActions(group.id)
        q.sql shouldBe s"SELECT actions FROM group_settings WHERE group_id=?"
        check(q)
      }
    }
  }
}

object GroupSettingsRepoSqlSpec {
  val meetupFields = "meetup_access_token, meetup_refresh_token, meetup_group_slug, meetup_logged_user_id, meetup_logged_user_name"
  val slackFields = "slack_token, slack_bot_name, slack_bot_avatar"
  val eventFields = "event_default_description, event_templates"

  val fieldList = s"group_id, $meetupFields, $slackFields, $eventFields, actions, updated, updated_by"
  val fieldListSelect = s"$meetupFields, $slackFields, $eventFields, actions"
}
