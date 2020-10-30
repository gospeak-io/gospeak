package gospeak.infra.services.storage.sql

import java.time.Instant

import cats.data.NonEmptyList
import cats.effect.IO
import doobie.syntax.string._
import fr.loicknuchel.safeql.Query
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
import gospeak.infra.services.storage.sql.database.tables.GROUP_SETTINGS
import gospeak.infra.services.storage.sql.utils.DoobieMappings._
import gospeak.infra.services.storage.sql.utils.GenericRepo
import gospeak.libs.scala.domain.{Liquid, LiquidMarkdown}

class GroupSettingsRepoSql(protected[sql] val xa: doobie.Transactor[IO], conf: GsConf) extends GenericRepo with GroupSettingsRepo {
  override def set(settings: Group.Settings)(implicit ctx: OrgaCtx): IO[Unit] = setSettings(ctx.group.id, settings)

  override def set(group: Group.Id, settings: Group.Settings)(implicit ctx: AdminCtx): IO[Unit] = setSettings(group, settings)

  private def setSettings(group: Group.Id, settings: Group.Settings)(implicit ctx: UserCtx): IO[Unit] =
    selectOne(group).run(xa).flatMap { opt =>
      opt.map { _ =>
        update(group, settings, ctx.user.id, ctx.now).run(xa)
      }.getOrElse {
        insert(group, settings, ctx.user.id, ctx.now).run(xa)
      }
    }

  override def list(groups: List[Group.Id])(implicit ctx: AdminCtx): IO[List[(Group.Id, Group.Settings)]] = runNel[Group.Id, (Group.Id, Group.Settings)](selectAll(_), groups)

  override def find(implicit ctx: OrgaCtx): IO[Group.Settings] =
    selectOne(ctx.group.id).run(xa).map(_.getOrElse(conf.defaultGroupSettings))

  override def find(group: Group.Id)(implicit ctx: AdminCtx): IO[Group.Settings] =
    selectOne(group).run(xa).map(_.getOrElse(conf.defaultGroupSettings))

  override def findAccounts(group: Group.Id): IO[Group.Settings.Accounts] =
    selectOneAccounts(group).run(xa).map(_.getOrElse(conf.defaultGroupSettings.accounts))

  override def findMeetup(implicit ctx: OrgaCtx): IO[Option[MeetupCredentials]] =
    selectOneMeetup(ctx.group.id).run(xa).map(_.getOrElse(conf.defaultGroupSettings.accounts.meetup))

  override def findMeetup(group: Group.Id)(implicit ctx: UserAwareCtx): IO[Option[MeetupCredentials]] =
    selectOneMeetup(group).run(xa).map(_.getOrElse(conf.defaultGroupSettings.accounts.meetup))

  override def findSlack(group: Group.Id): IO[Option[SlackCredentials]] =
    selectOneSlack(group).run(xa).map(_.getOrElse(conf.defaultGroupSettings.accounts.slack))

  override def findEventDescription(implicit ctx: OrgaCtx): IO[LiquidMarkdown[Message.EventInfo]] =
    selectOneEventDescription(ctx.group.id).run(xa).map(_.getOrElse(conf.defaultGroupSettings.event.description))

  override def findEventTemplates(implicit ctx: OrgaCtx): IO[Map[String, Liquid[Message.EventInfo]]] =
    selectOneEventTemplates(ctx.group.id).run(xa).map(_.getOrElse(conf.defaultGroupSettings.event.templates))

  override def findEventTemplates(group: Group.Id)(implicit ctx: UserAwareCtx): IO[Map[String, Liquid[Message.EventInfo]]] =
    selectOneEventTemplates(group).run(xa).map(_.getOrElse(conf.defaultGroupSettings.event.templates))

  override def findProposalTweet(group: Group.Id): IO[Liquid[Message.ProposalInfo]] =
    selectOneProposalTweet(group).run(xa).map(_.getOrElse(conf.defaultGroupSettings.proposal.tweet))

  override def findActions(group: Group.Id): IO[Map[Group.Settings.Action.Trigger, List[Group.Settings.Action]]] =
    selectOneActions(group).run(xa).map(_.getOrElse(conf.defaultGroupSettings.actions))
}

object GroupSettingsRepoSql {
  private val MEETUP_FIELDS = GROUP_SETTINGS.getFields.filter(_.name.startsWith("meetup_"))
  private val SLACK_FIELDS = GROUP_SETTINGS.getFields.filter(_.name.startsWith("slack_"))

  private[sql] def insert(group: Group.Id, settings: Group.Settings, by: User.Id, now: Instant): Query.Insert[GROUP_SETTINGS] =
  // GROUP_SETTINGS.insert.values(group,
  //   settings.accounts.meetup.map(_.accessToken), settings.accounts.meetup.map(_.refreshToken), settings.accounts.meetup.map(_.group), settings.accounts.meetup.map(_.loggedUserId), settings.accounts.meetup.map(_.loggedUserName),
  //   settings.accounts.slack.map(_.token), settings.accounts.slack.map(_.name), settings.accounts.slack.map(_.avatar),
  //   settings.event.description, settings.event.templates, settings.proposal.tweet, settings.actions, now, by)
    GROUP_SETTINGS.insert.values(fr0"$group, " ++
      fr0"${settings.accounts.meetup.map(_.accessToken)}, ${settings.accounts.meetup.map(_.refreshToken)}, ${settings.accounts.meetup.map(_.group)}, ${settings.accounts.meetup.map(_.loggedUserId)}, ${settings.accounts.meetup.map(_.loggedUserName)}, " ++
      fr0"${settings.accounts.slack.map(_.token)}, ${settings.accounts.slack.map(_.name)}, ${settings.accounts.slack.flatMap(_.avatar)}, " ++
      fr0"${settings.event.description}, ${settings.event.templates}, ${settings.proposal.tweet}, ${settings.actions}, $now, $by")

  private[sql] def update(group: Group.Id, settings: Group.Settings, by: User.Id, now: Instant): Query.Update[GROUP_SETTINGS] =
    GROUP_SETTINGS.update.set(_.MEETUP_ACCESS_TOKEN, settings.accounts.meetup.map(_.accessToken)).set(_.MEETUP_REFRESH_TOKEN, settings.accounts.meetup.map(_.refreshToken)).set(_.MEETUP_GROUP_SLUG, settings.accounts.meetup.map(_.group)).set(_.MEETUP_LOGGED_USER_ID, settings.accounts.meetup.map(_.loggedUserId)).set(_.MEETUP_LOGGED_USER_NAME, settings.accounts.meetup.map(_.loggedUserName))
      .set(_.SLACK_TOKEN, settings.accounts.slack.map(_.token)).set(_.SLACK_BOT_NAME, settings.accounts.slack.map(_.name)).set(_.SLACK_BOT_AVATAR, settings.accounts.slack.flatMap(_.avatar))
      .set(_.EVENT_DESCRIPTION, settings.event.description).set(_.EVENT_TEMPLATES, settings.event.templates).set(_.PROPOSAL_TWEET, settings.proposal.tweet)
      .set(_.ACTIONS, settings.actions).set(_.UPDATED_AT, now).set(_.UPDATED_BY, by)
      .where(_.GROUP_ID.is(group))

  private[sql] def selectAll(ids: NonEmptyList[Group.Id])(implicit ctx: AdminCtx): Query.Select.All[(Group.Id, Group.Settings)] =
    GROUP_SETTINGS.select.withoutFields(_.UPDATED_AT, _.UPDATED_BY).where(_.GROUP_ID.in(ids)).all[(Group.Id, Group.Settings)]

  private[sql] def selectOne(group: Group.Id): Query.Select.Optional[Group.Settings] =
    GROUP_SETTINGS.select.withoutFields(_.GROUP_ID, _.UPDATED_AT, _.UPDATED_BY).where(_.GROUP_ID.is(group)).option[Group.Settings]

  private[sql] def selectOneAccounts(group: Group.Id): Query.Select.Optional[Settings.Accounts] =
    GROUP_SETTINGS.select.fields(MEETUP_FIELDS ++ SLACK_FIELDS).where(_.GROUP_ID.is(group)).option[Settings.Accounts]

  private[sql] def selectOneMeetup(group: Group.Id): Query.Select.Optional[Option[MeetupCredentials]] =
    GROUP_SETTINGS.select.fields(MEETUP_FIELDS).where(_.GROUP_ID.is(group)).option[Option[MeetupCredentials]]

  private[sql] def selectOneSlack(group: Group.Id): Query.Select.Optional[Option[SlackCredentials]] =
    GROUP_SETTINGS.select.fields(SLACK_FIELDS).where(_.GROUP_ID.is(group)).option[Option[SlackCredentials]]

  private[sql] def selectOneEventDescription(group: Group.Id): Query.Select.Optional[LiquidMarkdown[Message.EventInfo]] =
    GROUP_SETTINGS.select.withFields(_.EVENT_DESCRIPTION).where(_.GROUP_ID.is(group)).option[LiquidMarkdown[Message.EventInfo]]

  private[sql] def selectOneEventTemplates(group: Group.Id): Query.Select.Optional[Map[String, Liquid[Message.EventInfo]]] =
    GROUP_SETTINGS.select.withFields(_.EVENT_TEMPLATES).where(_.GROUP_ID.is(group)).option[Map[String, Liquid[Message.EventInfo]]]

  private[sql] def selectOneProposalTweet(group: Group.Id): Query.Select.Optional[Liquid[Message.ProposalInfo]] =
    GROUP_SETTINGS.select.withFields(_.PROPOSAL_TWEET).where(_.GROUP_ID.is(group)).option[Liquid[Message.ProposalInfo]]

  private[sql] def selectOneActions(group: Group.Id): Query.Select.Optional[Map[Action.Trigger, List[Settings.Action]]] =
    GROUP_SETTINGS.select.withFields(_.ACTIONS).where(_.GROUP_ID.is(group)).option[Map[Group.Settings.Action.Trigger, List[Group.Settings.Action]]]
}
