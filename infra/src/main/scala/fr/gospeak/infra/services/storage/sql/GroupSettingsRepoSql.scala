package fr.gospeak.infra.services.storage.sql

import java.time.Instant

import cats.effect.IO
import doobie.implicits._
import fr.gospeak.core.GospeakConf
import fr.gospeak.core.domain.Group.Settings
import fr.gospeak.core.domain.Group.Settings.Action
import fr.gospeak.core.domain.utils.{OrgaCtx, TemplateData}
import fr.gospeak.core.domain.{Group, User}
import fr.gospeak.core.services.meetup.domain.MeetupCredentials
import fr.gospeak.core.services.slack.domain.SlackCredentials
import fr.gospeak.core.services.storage.GroupSettingsRepo
import fr.gospeak.infra.services.storage.sql.GroupSettingsRepoSql._
import fr.gospeak.infra.services.storage.sql.utils.DoobieUtils.Mappings._
import fr.gospeak.infra.services.storage.sql.utils.DoobieUtils.{Field, Insert, Select, Update}
import fr.gospeak.infra.services.storage.sql.utils.GenericRepo
import fr.gospeak.libs.scalautils.domain.Done
import fr.gospeak.libs.scalautils.domain.MustacheTmpl.{MustacheMarkdownTmpl, MustacheTextTmpl}

class GroupSettingsRepoSql(protected[sql] val xa: doobie.Transactor[IO], conf: GospeakConf) extends GenericRepo with GroupSettingsRepo {
  override def find(implicit ctx: OrgaCtx): IO[Group.Settings] =
    selectOne(ctx.group.id).runOption(xa).map(_.getOrElse(conf.defaultGroupSettings))

  override def findAccounts(group: Group.Id): IO[Group.Settings.Accounts] =
    selectOneAccounts(group).runOption(xa).map(_.getOrElse(conf.defaultGroupSettings.accounts))

  override def findMeetup(implicit ctx: OrgaCtx): IO[Option[MeetupCredentials]] =
    selectOneMeetup(ctx.group.id).runOption(xa).map(_.getOrElse(conf.defaultGroupSettings.accounts.meetup))

  override def findSlack(group: Group.Id): IO[Option[SlackCredentials]] =
    selectOneSlack(group).runOption(xa).map(_.getOrElse(conf.defaultGroupSettings.accounts.slack))

  override def findEventDescription(implicit ctx: OrgaCtx): IO[MustacheMarkdownTmpl[TemplateData.EventInfo]] =
    selectOneEventDescription(ctx.group.id).runOption(xa).map(_.getOrElse(conf.defaultGroupSettings.event.description))

  override def findEventTemplates(implicit ctx: OrgaCtx): IO[Map[String, MustacheTextTmpl[TemplateData.EventInfo]]] =
    selectOneEventTemplates(ctx.group.id).runOption(xa).map(_.getOrElse(conf.defaultGroupSettings.event.templates))

  override def findActions(group: Group.Id): IO[Map[Group.Settings.Action.Trigger, Seq[Group.Settings.Action]]] =
    selectOneActions(group).runOption(xa).map(_.getOrElse(conf.defaultGroupSettings.actions))

  override def set(settings: Group.Settings)(implicit ctx: OrgaCtx): IO[Done] =
    selectOne(ctx.group.id).runOption(xa).flatMap { opt =>
      opt.map { _ =>
        update(ctx.group.id, settings, ctx.user.id, ctx.now).run(xa)
      }.getOrElse {
        insert(ctx.group.id, settings, ctx.user.id, ctx.now).run(xa).map(_ => Done)
      }
    }
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
      fr0"${settings.event.description}, ${settings.event.templates}, ${settings.actions}, $now, $by"
    table.insert(settings, _ => values)
  }

  private[sql] def update(group: Group.Id, settings: Group.Settings, by: User.Id, now: Instant): Update = {
    val fields = fr0"meetup_access_token=${settings.accounts.meetup.map(_.accessToken)}, meetup_refresh_token=${settings.accounts.meetup.map(_.refreshToken)}, meetup_group_slug=${settings.accounts.meetup.map(_.group)}, meetup_logged_user_id=${settings.accounts.meetup.map(_.loggedUserId)}, meetup_logged_user_name=${settings.accounts.meetup.map(_.loggedUserName)}, " ++
      fr0"slack_token=${settings.accounts.slack.map(_.token)}, slack_bot_name=${settings.accounts.slack.map(_.name)}, slack_bot_avatar=${settings.accounts.slack.flatMap(_.avatar)}, " ++
      fr0"event_description=${settings.event.description}, event_templates=${settings.event.templates}, " ++
      fr0"actions=${settings.actions}, updated_at=$now, updated_by=$by"
    table.update(fields, where(group))
  }

  private[sql] def selectOne(group: Group.Id): Select[Group.Settings] =
    table.select[Group.Settings](table.fields.drop(1).dropRight(2), where(group))

  private[sql] def selectOneAccounts(group: Group.Id): Select[Settings.Accounts] =
    table.select[Group.Settings.Accounts](meetupFields ++ slackFields, where(group))

  private[sql] def selectOneMeetup(group: Group.Id): Select[Option[MeetupCredentials]] =
    table.select[Option[MeetupCredentials]](meetupFields, where(group))

  private[sql] def selectOneSlack(group: Group.Id): Select[Option[SlackCredentials]] =
    table.select[Option[SlackCredentials]](slackFields, where(group))

  private[sql] def selectOneEventDescription(group: Group.Id): Select[MustacheMarkdownTmpl[TemplateData.EventInfo]] =
    table.select[MustacheMarkdownTmpl[TemplateData.EventInfo]](Seq(Field("event_description", "gs")), where(group))

  private[sql] def selectOneEventTemplates(group: Group.Id): Select[Map[String, MustacheTextTmpl[TemplateData.EventInfo]]] =
    table.select[Map[String, MustacheTextTmpl[TemplateData.EventInfo]]](Seq(Field("event_templates", "gs")), where(group))

  private[sql] def selectOneActions(group: Group.Id): Select[Map[Action.Trigger, Seq[Settings.Action]]] =
    table.select[Map[Group.Settings.Action.Trigger, Seq[Group.Settings.Action]]](Seq(Field("actions", "gs")), where(group))

  private def where(group: Group.Id) = fr0"WHERE gs.group_id=$group"
}
