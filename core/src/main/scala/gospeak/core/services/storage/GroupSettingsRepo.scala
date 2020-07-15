package gospeak.core.services.storage

import cats.effect.IO
import gospeak.core.domain.Group
import gospeak.core.domain.messages.Message
import gospeak.core.domain.utils.{AdminCtx, OrgaCtx, UserAwareCtx}
import gospeak.core.services.meetup.domain.MeetupCredentials
import gospeak.core.services.slack.domain.SlackCredentials
import gospeak.libs.scala.domain.{Done, Mustache, MustacheMarkdown}

trait GroupSettingsRepo extends PublicGroupSettingsRepo with OrgaGroupSettingsRepo with AdminGroupSettingsRepo

trait PublicGroupSettingsRepo {
  def findMeetup(group: Group.Id)(implicit ctx: UserAwareCtx): IO[Option[MeetupCredentials]]

  def findEventTemplates(group: Group.Id)(implicit ctx: UserAwareCtx): IO[Map[String, Mustache[Message.EventInfo]]]

  def findProposalTweet(group: Group.Id): IO[Mustache[Message.ProposalInfo]]
}

trait OrgaGroupSettingsRepo {
  def find(implicit ctx: OrgaCtx): IO[Group.Settings]

  def findAccounts(group: Group.Id): IO[Group.Settings.Accounts]

  def findMeetup(implicit ctx: OrgaCtx): IO[Option[MeetupCredentials]]

  def findSlack(group: Group.Id): IO[Option[SlackCredentials]]

  def findEventDescription(implicit ctx: OrgaCtx): IO[MustacheMarkdown[Message.EventInfo]]

  def findEventTemplates(implicit ctx: OrgaCtx): IO[Map[String, Mustache[Message.EventInfo]]]

  def findProposalTweet(group: Group.Id): IO[Mustache[Message.ProposalInfo]]

  def findActions(group: Group.Id): IO[Map[Group.Settings.Action.Trigger, Seq[Group.Settings.Action]]]

  def set(settings: Group.Settings)(implicit ctx: OrgaCtx): IO[Done]
}

trait AdminGroupSettingsRepo {
  def list(groups: Seq[Group.Id])(implicit ctx: AdminCtx): IO[List[(Group.Id, Group.Settings)]]
}
