package fr.gospeak.core.services.storage

import java.time.Instant

import cats.effect.IO
import fr.gospeak.core.domain.utils.TemplateData
import fr.gospeak.core.domain.{Group, User}
import fr.gospeak.core.services.meetup.domain.MeetupCredentials
import fr.gospeak.core.services.slack.domain.SlackCredentials
import fr.gospeak.libs.scalautils.domain.Done
import fr.gospeak.libs.scalautils.domain.MustacheTmpl.{MustacheMarkdownTmpl, MustacheTextTmpl}

trait GroupSettingsRepo {
  def find(group: Group.Id): IO[Group.Settings]

  def findAccounts(group: Group.Id): IO[Group.Settings.Accounts]

  def findMeetup(group: Group.Id): IO[Option[MeetupCredentials]]

  def findSlack(group: Group.Id): IO[Option[SlackCredentials]]

  def findEventDescription(group: Group.Id): IO[MustacheMarkdownTmpl[TemplateData.EventInfo]]

  def findEventTemplates(group: Group.Id): IO[Map[String, MustacheTextTmpl[TemplateData.EventInfo]]]

  def findActions(group: Group.Id): IO[Map[Group.Settings.Action.Trigger, Seq[Group.Settings.Action]]]

  def set(group: Group.Id, settings: Group.Settings, by: User.Id, now: Instant): IO[Done]
}
