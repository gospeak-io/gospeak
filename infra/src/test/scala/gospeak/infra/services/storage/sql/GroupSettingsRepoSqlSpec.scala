package gospeak.infra.services.storage.sql

import gospeak.infra.services.storage.sql.GroupSettingsRepoSqlSpec._
import gospeak.infra.services.storage.sql.testingutils.RepoSpec
import gospeak.infra.services.storage.sql.testingutils.RepoSpec.mapFields

class GroupSettingsRepoSqlSpec extends RepoSpec {
  describe("GroupSettingsRepoSql") {
    describe("Queries") {
      it("should build insert group settings") {
        val q = GroupSettingsRepoSql.insert(group.id, groupSettings, user.id, now)
        check(q, s"INSERT INTO ${table.stripSuffix(" gs")} (${mapFields(fields, _.stripPrefix("gs."))}) VALUES (${mapFields(fields, _ => "?")})")
      }
      it("should build update group settings") {
        val q = GroupSettingsRepoSql.update(group.id, groupSettings, user.id, now)
        check(q, s"UPDATE $table SET ${fields.split(", ").drop(1).map(_.stripPrefix("gs.") + "=?").mkString(", ")} WHERE gs.group_id=?")
      }
      it("should build selectOne group settings") {
        val q = GroupSettingsRepoSql.selectOne(group.id)
        check(q, s"SELECT $fieldsSelect FROM $table WHERE gs.group_id=?")
      }
      it("should build selectOneAccounts group settings") {
        val q = GroupSettingsRepoSql.selectOneAccounts(group.id)
        check(q, s"SELECT $meetupFields, $slackFields FROM $table WHERE gs.group_id=?")
      }
      it("should build selectOneMeetup group settings") {
        val q = GroupSettingsRepoSql.selectOneMeetup(group.id)
        check(q, s"SELECT $meetupFields FROM $table WHERE gs.group_id=?")
      }
      it("should build selectOneSlack group settings") {
        val q = GroupSettingsRepoSql.selectOneSlack(group.id)
        check(q, s"SELECT $slackFields FROM $table WHERE gs.group_id=?")
      }
      it("should build selectOneEventDescription group settings") {
        val q = GroupSettingsRepoSql.selectOneEventDescription(group.id)
        check(q, s"SELECT gs.event_description FROM $table WHERE gs.group_id=?")
      }
      it("should build selectOneEventTemplates group settings") {
        val q = GroupSettingsRepoSql.selectOneEventTemplates(group.id)
        check(q, s"SELECT gs.event_templates FROM $table WHERE gs.group_id=?")
      }
      it("should build selectOneActions group settings") {
        val q = GroupSettingsRepoSql.selectOneActions(group.id)
        check(q, s"SELECT gs.actions FROM $table WHERE gs.group_id=?")
      }
    }
  }
}

object GroupSettingsRepoSqlSpec {
  val meetupFields: String = mapFields("meetup_access_token, meetup_refresh_token, meetup_group_slug, meetup_logged_user_id, meetup_logged_user_name", "gs." + _)
  val slackFields: String = mapFields("slack_token, slack_bot_name, slack_bot_avatar", "gs." + _)
  val eventFields: String = mapFields("event_description, event_templates", "gs." + _)

  val table = "group_settings gs"
  val fields = s"gs.group_id, $meetupFields, $slackFields, $eventFields, gs.actions, gs.updated_at, gs.updated_by"
  val fieldsSelect = s"$meetupFields, $slackFields, $eventFields, gs.actions"
}
