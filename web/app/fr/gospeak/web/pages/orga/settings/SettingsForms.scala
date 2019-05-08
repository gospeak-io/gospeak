package fr.gospeak.web.pages.orga.settings

import fr.gospeak.core.domain.Group.Settings.Events
import fr.gospeak.core.services.slack.domain.SlackCredentials
import fr.gospeak.web.utils.Mappings._
import play.api.data.Form
import play.api.data.Forms._

object SettingsForms {
  val slackAccount: Form[SlackCredentials] = Form(mapping(
    "token" -> slackToken,
    "name" -> nonEmptyText,
    "avatar" -> optional(nonEmptyText)
  )(SlackCredentials.apply)(SlackCredentials.unapply))

  final case class AddAction(event: Events.Event, action: Events.Action)

  val addAction: Form[AddAction] = Form(mapping(
    "event" -> groupSettingsEvent,
    "action" -> groupSettingsAction
  )(AddAction.apply)(AddAction.unapply))
}
