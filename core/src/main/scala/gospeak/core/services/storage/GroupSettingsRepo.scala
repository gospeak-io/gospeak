package gospeak.core.services.storage

import cats.effect.IO
import gospeak.core.domain.Group
import gospeak.core.domain.messages.Message
import gospeak.core.domain.utils.{AdminCtx, OrgaCtx, UserAwareCtx}
import gospeak.core.services.meetup.domain.MeetupCredentials
import gospeak.core.services.slack.domain.SlackCredentials
import gospeak.libs.scala.domain.{Liquid, LiquidMarkdown}

trait GroupSettingsRepo extends PublicGroupSettingsRepo with OrgaGroupSettingsRepo with AdminGroupSettingsRepo

trait PublicGroupSettingsRepo {
  def findMeetup(group: Group.Id)(implicit ctx: UserAwareCtx): IO[Option[MeetupCredentials]]

  def findEventTemplates(group: Group.Id)(implicit ctx: UserAwareCtx): IO[Map[String, Liquid[Message.EventInfo]]]

  def findProposalTweet(group: Group.Id): IO[Liquid[Message.ProposalInfo]]
}

trait OrgaGroupSettingsRepo {
  def find(implicit ctx: OrgaCtx): IO[Group.Settings]

  def findAccounts(group: Group.Id): IO[Group.Settings.Accounts]

  def findMeetup(implicit ctx: OrgaCtx): IO[Option[MeetupCredentials]]

  def findSlack(group: Group.Id): IO[Option[SlackCredentials]]

  def findEventDescription(implicit ctx: OrgaCtx): IO[LiquidMarkdown[Message.EventInfo]]

  def findEventTemplates(implicit ctx: OrgaCtx): IO[Map[String, Liquid[Message.EventInfo]]]

  def findProposalTweet(group: Group.Id): IO[Liquid[Message.ProposalInfo]]

  def findActions(group: Group.Id): IO[Map[Group.Settings.Action.Trigger, List[Group.Settings.Action]]]

  def set(settings: Group.Settings)(implicit ctx: OrgaCtx): IO[Unit]
}

trait AdminGroupSettingsRepo {
  def list(groups: List[Group.Id])(implicit ctx: AdminCtx): IO[List[(Group.Id, Group.Settings)]]

  def find(group: Group.Id)(implicit ctx: AdminCtx): IO[Group.Settings]

  def set(group: Group.Id, settings: Group.Settings)(implicit ctx: AdminCtx): IO[Unit]
}
