package gospeak.infra.services.storage.sql

import cats.data.NonEmptyList
import gospeak.infra.services.storage.sql.GroupSettingsRepoSql._
import gospeak.infra.services.storage.sql.GroupSettingsRepoSqlSpec._
import gospeak.infra.services.storage.sql.testingutils.RepoSpec
import gospeak.infra.services.storage.sql.testingutils.RepoSpec.mapFields
import gospeak.infra.testingutils.Values

class GroupSettingsRepoSqlSpec extends RepoSpec {
  describe("GroupSettingsRepoSql") {
    it("should handle crud operations") {
      val (user, group, ctx) = createOrga().unsafeRunSync()
      groupSettingsRepo.find(ctx).unsafeRunSync() shouldBe Values.gsConf.defaultGroupSettings
      groupSettingsRepo.set(groupSettings)(ctx).unsafeRunSync()
      groupSettingsRepo.find(ctx).unsafeRunSync() shouldBe groupSettings

      groupSettingsRepo.set(group.id, groupSettings2)(ctx.adminCtx).unsafeRunSync()
      groupSettingsRepo.find(ctx).unsafeRunSync() shouldBe groupSettings2
    }
    it("should be able to read correctly") {
      val (user, group, ctx) = createOrga().unsafeRunSync()
      groupSettingsRepo.set(groupSettings)(ctx).unsafeRunSync()

      groupSettingsRepo.list(List(group.id))(ctx.adminCtx).unsafeRunSync() shouldBe List(group.id -> groupSettings)
      groupSettingsRepo.find(ctx).unsafeRunSync() shouldBe groupSettings
      groupSettingsRepo.find(group.id)(ctx.adminCtx).unsafeRunSync() shouldBe groupSettings
      groupSettingsRepo.findAccounts(group.id).unsafeRunSync() shouldBe groupSettings.accounts
      groupSettingsRepo.findMeetup(ctx).unsafeRunSync() shouldBe groupSettings.accounts.meetup
      groupSettingsRepo.findMeetup(group.id)(ctx.userAwareCtx).unsafeRunSync() shouldBe groupSettings.accounts.meetup
      groupSettingsRepo.findSlack(group.id).unsafeRunSync() shouldBe groupSettings.accounts.slack
      groupSettingsRepo.findEventDescription(ctx).unsafeRunSync() shouldBe groupSettings.event.description
      groupSettingsRepo.findEventTemplates(ctx).unsafeRunSync() shouldBe groupSettings.event.templates
      groupSettingsRepo.findEventTemplates(group.id)(ctx.userAwareCtx).unsafeRunSync() shouldBe groupSettings.event.templates
      groupSettingsRepo.findProposalTweet(group.id).unsafeRunSync() shouldBe groupSettings.proposal.tweet
      groupSettingsRepo.findActions(group.id).unsafeRunSync() shouldBe groupSettings.actions
    }
    it("should check queries") {
      check(insert(group.id, groupSettings, user.id, now), s"INSERT INTO ${table.stripSuffix(" gs")} (${mapFields(fields, _.stripPrefix("gs."))}) VALUES (${mapFields(fields, _ => "?")})")
      check(update(group.id, groupSettings, user.id, now), s"UPDATE $table SET ${fields.split(", ").drop(1).map(_.stripPrefix("gs.") + "=?").mkString(", ")} WHERE gs.group_id=?")
      check(selectAll(NonEmptyList.of(group.id))(adminCtx), s"SELECT gs.group_id, $fieldsSelect FROM $table WHERE gs.group_id IN (?) $orderBy")
      check(selectOne(group.id), s"SELECT $fieldsSelect FROM $table WHERE gs.group_id=? $orderBy")
      check(selectOneAccounts(group.id), s"SELECT $meetupFields, $slackFields FROM $table WHERE gs.group_id=? $orderBy")
      check(selectOneMeetup(group.id), s"SELECT $meetupFields FROM $table WHERE gs.group_id=? $orderBy")
      check(selectOneSlack(group.id), s"SELECT $slackFields FROM $table WHERE gs.group_id=? $orderBy")
      check(selectOneEventDescription(group.id), s"SELECT gs.event_description FROM $table WHERE gs.group_id=? $orderBy")
      check(selectOneEventTemplates(group.id), s"SELECT gs.event_templates FROM $table WHERE gs.group_id=? $orderBy")
      check(selectOneProposalTweet(group.id), s"SELECT gs.proposal_tweet FROM $table WHERE gs.group_id=? $orderBy")
      check(selectOneActions(group.id), s"SELECT gs.actions FROM $table WHERE gs.group_id=? $orderBy")
    }
  }
}

object GroupSettingsRepoSqlSpec {
  val meetupFields: String = mapFields("meetup_access_token, meetup_refresh_token, meetup_group_slug, meetup_logged_user_id, meetup_logged_user_name", "gs." + _)
  val slackFields: String = mapFields("slack_token, slack_bot_name, slack_bot_avatar", "gs." + _)
  val eventFields: String = mapFields("event_description, event_templates", "gs." + _)
  val proposalFields: String = mapFields("proposal_tweet", "gs." + _)

  val table = "group_settings gs"
  val fields = s"gs.group_id, $meetupFields, $slackFields, $eventFields, $proposalFields, gs.actions, gs.updated_at, gs.updated_by"
  val fieldsSelect = s"$meetupFields, $slackFields, $eventFields, $proposalFields, gs.actions"
  val orderBy = "ORDER BY gs.group_id IS NULL, gs.group_id"
}
