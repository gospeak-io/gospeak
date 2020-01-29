package gospeak.core.services.storage

import cats.effect.IO
import gospeak.core.domain.Group
import gospeak.core.domain.utils.{OrgaCtx, TemplateData, UserAwareCtx}
import gospeak.core.services.meetup.domain.MeetupCredentials
import gospeak.core.services.slack.domain.SlackCredentials
import gospeak.libs.scala.domain.Done
import gospeak.libs.scala.domain.MustacheTmpl.{MustacheMarkdownTmpl, MustacheTextTmpl}

trait GroupSettingsRepo extends OrgaGroupSettingsRepo with PublicGroupSettingsRepo

trait OrgaGroupSettingsRepo {
  def find(implicit ctx: OrgaCtx): IO[Group.Settings]

  def findAccounts(group: Group.Id): IO[Group.Settings.Accounts]

  def findMeetup(implicit ctx: OrgaCtx): IO[Option[MeetupCredentials]]

  def findSlack(group: Group.Id): IO[Option[SlackCredentials]]

  def findEventDescription(implicit ctx: OrgaCtx): IO[MustacheMarkdownTmpl[TemplateData.EventInfo]]

  def findEventTemplates(implicit ctx: OrgaCtx): IO[Map[String, MustacheTextTmpl[TemplateData.EventInfo]]]

  def findActions(group: Group.Id): IO[Map[Group.Settings.Action.Trigger, Seq[Group.Settings.Action]]]

  def set(settings: Group.Settings)(implicit ctx: OrgaCtx): IO[Done]
}

trait PublicGroupSettingsRepo {
  def findMeetup(group: Group.Id)(implicit ctx: UserAwareCtx): IO[Option[MeetupCredentials]]
}
