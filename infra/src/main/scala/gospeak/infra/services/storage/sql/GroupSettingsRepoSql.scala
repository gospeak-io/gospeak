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
import gospeak.infra.services.storage.sql.utils.DoobieUtils.Mappings._
import gospeak.infra.services.storage.sql.utils.DoobieUtils.{Field, Insert, Select, Update}
import gospeak.infra.services.storage.sql.utils.GenericRepo
import gospeak.libs.scala.domain.{Done, Liquid, LiquidMarkdown}

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

  override def list(groups: Seq[Group.Id])(implicit ctx: AdminCtx): IO[List[(Group.Id, Group.Settings)]] = runNel[Group.Id, (Group.Id, Group.Settings)](selectAll(_), groups)

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

  override def findActions(group: Group.Id): IO[Map[Group.Settings.Action.Trigger, Seq[Group.Settings.Action]]] =
    selectOneActions(group).runOption(xa).map(_.getOrElse(conf.defaultGroupSettings.actions))
}

object GroupSettingsRepoSql {
  private val _ = groupIdMeta // for intellij not remove DoobieUtils.Mappings import
  private val table = Tables.groupSettings
  private val meetupFields = Seq("meetup_access_token", "meetup_refresh_token", "meetup_group_slug", "meetup_logged_user_id", "meetup_logged_user_name").map(n => Field(n, table.prefix))
  private val slackFields = Seq("slack_token", "slack_bot_name", "slack_bot_avatar").map(n => Field(n, table.prefix))

  private[sql] def insert(group: Group.Id, settings: Group.Settings, by: User.Id, now: Instant): Insert[Group.Settings] = {
    val values = fr0"$group, " ++
      fr0"${settings.accounts.meetup.map(_.accessToken)}, ${settings.accounts.meetup.map(_.refreshToken)}, ${settings.accounts.meetup.map(_.group)}, ${settings.accounts.meetup.map(_.loggedUserId)}, ${settings.accounts.meetup.map(_.loggedUserName)}, " ++
      fr0"${settings.accounts.slack.map(_.token)}, ${settings.accounts.slack.map(_.name)}, ${settings.accounts.slack.flatMap(_.avatar)}, " ++
      fr0"${settings.event.description}, ${settings.event.templates}, ${settings.proposal.tweet}, ${settings.actions}, $now, $by"
    table.insert(settings, _ => values)
  }

  private[sql] def update(group: Group.Id, settings: Group.Settings, by: User.Id, now: Instant): Update = {
    val fields = fr0"meetup_access_token=${settings.accounts.meetup.map(_.accessToken)}, meetup_refresh_token=${settings.accounts.meetup.map(_.refreshToken)}, meetup_group_slug=${settings.accounts.meetup.map(_.group)}, meetup_logged_user_id=${settings.accounts.meetup.map(_.loggedUserId)}, meetup_logged_user_name=${settings.accounts.meetup.map(_.loggedUserName)}, " ++
      fr0"slack_token=${settings.accounts.slack.map(_.token)}, slack_bot_name=${settings.accounts.slack.map(_.name)}, slack_bot_avatar=${settings.accounts.slack.flatMap(_.avatar)}, " ++
      fr0"event_description=${settings.event.description}, event_templates=${settings.event.templates}, proposal_tweet=${settings.proposal.tweet}, " ++
      fr0"actions=${settings.actions}, updated_at=$now, updated_by=$by"
    table.update(fields, where(group))
  }

  private[sql] def selectAll(ids: NonEmptyList[Group.Id])(implicit ctx: AdminCtx): Select[(Group.Id, Group.Settings)] =
    table.select[(Group.Id, Group.Settings)](table.fields.dropRight(2), fr0"WHERE " ++ Fragments.in(fr"gs.group_id", ids))

  private[sql] def selectOne(group: Group.Id): Select[Group.Settings] =
    table.select[Group.Settings](table.fields.drop(1).dropRight(2), where(group))

  private[sql] def selectOneAccounts(group: Group.Id): Select[Settings.Accounts] =
    table.select[Group.Settings.Accounts](meetupFields ++ slackFields, where(group))

  private[sql] def selectOneMeetup(group: Group.Id): Select[Option[MeetupCredentials]] =
    table.select[Option[MeetupCredentials]](meetupFields, where(group))

  private[sql] def selectOneSlack(group: Group.Id): Select[Option[SlackCredentials]] =
    table.select[Option[SlackCredentials]](slackFields, where(group))

  private[sql] def selectOneEventDescription(group: Group.Id): Select[LiquidMarkdown[Message.EventInfo]] =
    table.select[LiquidMarkdown[Message.EventInfo]](Seq(Field("event_description", "gs")), where(group))

  private[sql] def selectOneEventTemplates(group: Group.Id): Select[Map[String, Liquid[Message.EventInfo]]] =
    table.select[Map[String, Liquid[Message.EventInfo]]](Seq(Field("event_templates", "gs")), where(group))

  private[sql] def selectOneProposalTweet(group: Group.Id): Select[Liquid[Message.ProposalInfo]] =
    table.select[Liquid[Message.ProposalInfo]](Seq(Field("proposal_tweet", "gs")), where(group))

  private[sql] def selectOneActions(group: Group.Id): Select[Map[Action.Trigger, Seq[Settings.Action]]] =
    table.select[Map[Group.Settings.Action.Trigger, Seq[Group.Settings.Action]]](Seq(Field("actions", "gs")), where(group))

  private def where(group: Group.Id) = fr0"WHERE gs.group_id=$group"
}
