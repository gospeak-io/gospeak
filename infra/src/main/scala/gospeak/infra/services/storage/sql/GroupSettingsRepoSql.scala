package gospeak.infra.services.storage.sql

import java.time.Instant

import cats.data.NonEmptyList
import cats.effect.IO
import doobie.Fragments
import doobie.implicits._
import gospeak.core.GsConf
import gospeak.core.domain.Group.Settings
import gospeak.core.domain.Group.Settings.Action
import gospeak.core.domain.messages.Message
import gospeak.core.domain.utils.{AdminCtx, OrgaCtx, UserAwareCtx, UserCtx}
import gospeak.core.domain.{Group, User}
import gospeak.core.services.meetup.domain.MeetupCredentials
import gospeak.core.services.slack.domain.SlackCredentials
import gospeak.core.services.storage.GroupSettingsRepo
import gospeak.infra.services.storage.sql.GroupSettingsRepoSql._
import gospeak.infra.services.storage.sql.database.Tables.GROUP_SETTINGS
import gospeak.infra.services.storage.sql.utils.DoobieMappings._
import gospeak.infra.services.storage.sql.utils.GenericRepo
import gospeak.libs.scala.domain.{Done, Liquid, LiquidMarkdown}
import gospeak.libs.sql.doobie.{Field, Query}

class GroupSettingsRepoSql(protected[sql] val xa: doobie.Transactor[IO], conf: GsConf) extends GenericRepo with GroupSettingsRepo {
  override def set(settings: Group.Settings)(implicit ctx: OrgaCtx): IO[Done] = setSettings(ctx.group.id, settings)

  override def set(group: Group.Id, settings: Group.Settings)(implicit ctx: AdminCtx): IO[Done] = setSettings(group, settings)

  private def setSettings(group: Group.Id, settings: Group.Settings)(implicit ctx: UserCtx): IO[Done] =
    selectOne(group).runOption(xa).flatMap { opt =>
      opt.map { _ =>
        update(group, settings, ctx.user.id, ctx.now).run(xa)
      }.getOrElse {
        insert(group, settings, ctx.user.id, ctx.now).run(xa).map(_ => Done)
      }
    }

  override def list(groups: List[Group.Id])(implicit ctx: AdminCtx): IO[List[(Group.Id, Group.Settings)]] = runNel[Group.Id, (Group.Id, Group.Settings)](selectAll(_), groups)

  override def find(implicit ctx: OrgaCtx): IO[Group.Settings] =
    selectOne(ctx.group.id).runOption(xa).map(_.getOrElse(conf.defaultGroupSettings))

  override def find(group: Group.Id)(implicit ctx: AdminCtx): IO[Group.Settings] =
    selectOne(group).runOption(xa).map(_.getOrElse(conf.defaultGroupSettings))

  override def findAccounts(group: Group.Id): IO[Group.Settings.Accounts] =
    selectOneAccounts(group).runOption(xa).map(_.getOrElse(conf.defaultGroupSettings.accounts))

  override def findMeetup(implicit ctx: OrgaCtx): IO[Option[MeetupCredentials]] =
    selectOneMeetup(ctx.group.id).runOption(xa).map(_.getOrElse(conf.defaultGroupSettings.accounts.meetup))

  override def findMeetup(group: Group.Id)(implicit ctx: UserAwareCtx): IO[Option[MeetupCredentials]] =
    selectOneMeetup(group).runOption(xa).map(_.getOrElse(conf.defaultGroupSettings.accounts.meetup))

  override def findSlack(group: Group.Id): IO[Option[SlackCredentials]] =
    selectOneSlack(group).runOption(xa).map(_.getOrElse(conf.defaultGroupSettings.accounts.slack))

  override def findEventDescription(implicit ctx: OrgaCtx): IO[LiquidMarkdown[Message.EventInfo]] =
    selectOneEventDescription(ctx.group.id).runOption(xa).map(_.getOrElse(conf.defaultGroupSettings.event.description))

  override def findEventTemplates(implicit ctx: OrgaCtx): IO[Map[String, Liquid[Message.EventInfo]]] =
    selectOneEventTemplates(ctx.group.id).runOption(xa).map(_.getOrElse(conf.defaultGroupSettings.event.templates))

  override def findEventTemplates(group: Group.Id)(implicit ctx: UserAwareCtx): IO[Map[String, Liquid[Message.EventInfo]]] =
    selectOneEventTemplates(group).runOption(xa).map(_.getOrElse(conf.defaultGroupSettings.event.templates))

  override def findProposalTweet(group: Group.Id): IO[Liquid[Message.ProposalInfo]] =
    selectOneProposalTweet(group).runOption(xa).map(_.getOrElse(conf.defaultGroupSettings.proposal.tweet))

  override def findActions(group: Group.Id): IO[Map[Group.Settings.Action.Trigger, List[Group.Settings.Action]]] =
    selectOneActions(group).runOption(xa).map(_.getOrElse(conf.defaultGroupSettings.actions))
}

object GroupSettingsRepoSql {
  private val _ = groupIdMeta // for intellij not remove DoobieMappings import
  private val table = Tables.groupSettings
  private val meetupFields = List("meetup_access_token", "meetup_refresh_token", "meetup_group_slug", "meetup_logged_user_id", "meetup_logged_user_name").map(n => Field(n, table.prefix))
  private val slackFields = List("slack_token", "slack_bot_name", "slack_bot_avatar").map(n => Field(n, table.prefix))
  private val MEETUP_FIELDS = GROUP_SETTINGS.getFields.filter(_.name.startsWith("meetup_"))
  private val SLACK_FIELDS = GROUP_SETTINGS.getFields.filter(_.name.startsWith("slack_"))

  private[sql] def insert(group: Group.Id, settings: Group.Settings, by: User.Id, now: Instant): Query.Insert[Group.Settings] = {
    val values = fr0"$group, " ++
      fr0"${settings.accounts.meetup.map(_.accessToken)}, ${settings.accounts.meetup.map(_.refreshToken)}, ${settings.accounts.meetup.map(_.group)}, ${settings.accounts.meetup.map(_.loggedUserId)}, ${settings.accounts.meetup.map(_.loggedUserName)}, " ++
      fr0"${settings.accounts.slack.map(_.token)}, ${settings.accounts.slack.map(_.name)}, ${settings.accounts.slack.flatMap(_.avatar)}, " ++
      fr0"${settings.event.description}, ${settings.event.templates}, ${settings.proposal.tweet}, ${settings.actions}, $now, $by"
    val q1 = table.insert[Group.Settings](settings, _ => values)
    val q2 = GROUP_SETTINGS.insert.values(group,
      settings.accounts.meetup.map(_.accessToken), settings.accounts.meetup.map(_.refreshToken), settings.accounts.meetup.map(_.group), settings.accounts.meetup.map(_.loggedUserId), settings.accounts.meetup.map(_.loggedUserName),
      settings.accounts.slack.map(_.token), settings.accounts.slack.map(_.name), settings.accounts.slack.map(_.avatar),
      settings.event.description, settings.event.templates, settings.proposal.tweet, settings.actions, now, by)
    GenericRepo.assertEqual(q1.fr, q2.fr)
    q1
  }

  private[sql] def update(group: Group.Id, settings: Group.Settings, by: User.Id, now: Instant): Query.Update = {
    val fields = fr0"meetup_access_token=${settings.accounts.meetup.map(_.accessToken)}, meetup_refresh_token=${settings.accounts.meetup.map(_.refreshToken)}, meetup_group_slug=${settings.accounts.meetup.map(_.group)}, meetup_logged_user_id=${settings.accounts.meetup.map(_.loggedUserId)}, meetup_logged_user_name=${settings.accounts.meetup.map(_.loggedUserName)}, " ++
      fr0"slack_token=${settings.accounts.slack.map(_.token)}, slack_bot_name=${settings.accounts.slack.map(_.name)}, slack_bot_avatar=${settings.accounts.slack.flatMap(_.avatar)}, " ++
      fr0"event_description=${settings.event.description}, event_templates=${settings.event.templates}, proposal_tweet=${settings.proposal.tweet}, " ++
      fr0"actions=${settings.actions}, updated_at=$now, updated_by=$by"
    val q1 = table.update(fields).where(where(group))
    val q2 = GROUP_SETTINGS.update.set(_.MEETUP_ACCESS_TOKEN, settings.accounts.meetup.map(_.accessToken)).set(_.MEETUP_REFRESH_TOKEN, settings.accounts.meetup.map(_.refreshToken)).set(_.MEETUP_GROUP_SLUG, settings.accounts.meetup.map(_.group)).set(_.MEETUP_LOGGED_USER_ID, settings.accounts.meetup.map(_.loggedUserId)).set(_.MEETUP_LOGGED_USER_NAME, settings.accounts.meetup.map(_.loggedUserName))
      .set(_.SLACK_TOKEN, settings.accounts.slack.map(_.token)).set(_.SLACK_BOT_NAME, settings.accounts.slack.map(_.name)).set(_.SLACK_BOT_AVATAR, settings.accounts.slack.flatMap(_.avatar))
      .set(_.EVENT_DESCRIPTION, settings.event.description).set(_.EVENT_TEMPLATES, settings.event.templates).set(_.PROPOSAL_TWEET, settings.proposal.tweet)
      .set(_.ACTIONS, settings.actions).set(_.UPDATED_AT, now).set(_.UPDATED_BY, by)
      .where(_.GROUP_ID.is(group))
    GenericRepo.assertEqual(q1.fr, q2.fr)
    q1
  }

  private[sql] def selectAll(ids: NonEmptyList[Group.Id])(implicit ctx: AdminCtx): Query.Select[(Group.Id, Group.Settings)] = {
    val q1 = table.select[(Group.Id, Group.Settings)].fields(table.fields.dropRight(2)).where(Fragments.in(fr"gs.group_id", ids))
    val q2 = GROUP_SETTINGS.select.withoutFields(_.UPDATED_AT, _.UPDATED_BY).where(_.GROUP_ID.in(ids)).all[(Group.Id, Group.Settings)]
    GenericRepo.assertEqual(q1.fr, q2.fr)
    q1
  }

  private[sql] def selectOne(group: Group.Id): Query.Select[Group.Settings] = {
    val q1 = table.select[Group.Settings].fields(table.fields.drop(1).dropRight(2)).where(where(group))
    val q2 = GROUP_SETTINGS.select.withoutFields(_.GROUP_ID, _.UPDATED_AT, _.UPDATED_BY).where(_.GROUP_ID.is(group)).option[Group.Settings]
    GenericRepo.assertEqual(q1.fr, q2.fr)
    q1
  }

  private[sql] def selectOneAccounts(group: Group.Id): Query.Select[Settings.Accounts] = {
    val q1 = table.select[Group.Settings.Accounts].fields(meetupFields ++ slackFields).where(where(group))
    val q2 = GROUP_SETTINGS.select.fields(MEETUP_FIELDS ++ SLACK_FIELDS).where(_.GROUP_ID.is(group)).option[Settings.Accounts]
    GenericRepo.assertEqual(q1.fr, q2.fr)
    q1
  }

  private[sql] def selectOneMeetup(group: Group.Id): Query.Select[Option[MeetupCredentials]] = {
    val q1 = table.select[Option[MeetupCredentials]].fields(meetupFields).where(where(group))
    val q2 = GROUP_SETTINGS.select.fields(MEETUP_FIELDS).where(_.GROUP_ID.is(group)).option[Option[MeetupCredentials]]
    GenericRepo.assertEqual(q1.fr, q2.fr)
    q1
  }

  private[sql] def selectOneSlack(group: Group.Id): Query.Select[Option[SlackCredentials]] = {
    val q1 = table.select[Option[SlackCredentials]].fields(slackFields).where(where(group))
    val q2 = GROUP_SETTINGS.select.fields(SLACK_FIELDS).where(_.GROUP_ID.is(group)).option[Option[SlackCredentials]]
    GenericRepo.assertEqual(q1.fr, q2.fr)
    q1
  }

  private[sql] def selectOneEventDescription(group: Group.Id): Query.Select[LiquidMarkdown[Message.EventInfo]] = {
    val q1 = table.select[LiquidMarkdown[Message.EventInfo]].fields(Field("event_description", "gs")).where(where(group))
    val q2 = GROUP_SETTINGS.select.withFields(_.EVENT_DESCRIPTION).where(_.GROUP_ID.is(group)).option[LiquidMarkdown[Message.EventInfo]]
    GenericRepo.assertEqual(q1.fr, q2.fr)
    q1
  }

  private[sql] def selectOneEventTemplates(group: Group.Id): Query.Select[Map[String, Liquid[Message.EventInfo]]] = {
    val q1 = table.select[Map[String, Liquid[Message.EventInfo]]].fields(Field("event_templates", "gs")).where(where(group))
    val q2 = GROUP_SETTINGS.select.withFields(_.EVENT_TEMPLATES).where(_.GROUP_ID.is(group)).option[Map[String, Liquid[Message.EventInfo]]]
    GenericRepo.assertEqual(q1.fr, q2.fr)
    q1
  }

  private[sql] def selectOneProposalTweet(group: Group.Id): Query.Select[Liquid[Message.ProposalInfo]] = {
    val q1 = table.select[Liquid[Message.ProposalInfo]].fields(Field("proposal_tweet", "gs")).where(where(group))
    val q2 = GROUP_SETTINGS.select.withFields(_.PROPOSAL_TWEET).where(_.GROUP_ID.is(group)).option[Liquid[Message.ProposalInfo]]
    GenericRepo.assertEqual(q1.fr, q2.fr)
    q1
  }

  private[sql] def selectOneActions(group: Group.Id): Query.Select[Map[Action.Trigger, List[Settings.Action]]] = {
    val q1 = table.select[Map[Group.Settings.Action.Trigger, List[Group.Settings.Action]]].fields(Field("actions", "gs")).where(where(group))
    val q2 = GROUP_SETTINGS.select.withFields(_.ACTIONS).where(_.GROUP_ID.is(group)).option[Map[Group.Settings.Action.Trigger, List[Group.Settings.Action]]]
    GenericRepo.assertEqual(q1.fr, q2.fr)
    q1
  }

  private def where(group: Group.Id) = fr0"gs.group_id=$group"
}
