package fr.gospeak.web.pages.orga.settings

import fr.gospeak.core.domain.Group.Settings
import fr.gospeak.core.domain.utils.TemplateData
import fr.gospeak.core.services.meetup.domain.MeetupGroup
import fr.gospeak.core.services.slack.domain.SlackCredentials
import fr.gospeak.libs.scalautils.domain.MustacheTmpl.MustacheMarkdownTmpl
import fr.gospeak.web.utils.Mappings._
import play.api.data.Form
import play.api.data.Forms._

object SettingsForms {

  final case class MeetupAccount(group: MeetupGroup.Slug)

  val meetupAccount: Form[MeetupAccount] = Form(mapping(
    "group" -> meetupGroupSlug
  )(MeetupAccount.apply)(MeetupAccount.unapply))

  val slackAccount: Form[SlackCredentials] = Form(mapping(
    "token" -> slackToken,
    "name" -> nonEmptyText,
    "avatar" -> optional(url)
  )(SlackCredentials.apply)(SlackCredentials.unapply))

  final case class AddAction(trigger: Settings.Action.Trigger, action: Settings.Action)

  val addAction: Form[AddAction] = Form(mapping(
    "trigger" -> groupSettingsEvent,
    "action" -> groupSettingsAction
  )(AddAction.apply)(AddAction.unapply))

  case class EventTemplateItem(id: String, template: MustacheMarkdownTmpl[TemplateData.EventInfo])

  val eventTemplateItem: Form[EventTemplateItem] = Form(mapping(
    "id" -> nonEmptyText,
    "template" -> template[TemplateData.EventInfo]
  )(EventTemplateItem.apply)(EventTemplateItem.unapply))
}
