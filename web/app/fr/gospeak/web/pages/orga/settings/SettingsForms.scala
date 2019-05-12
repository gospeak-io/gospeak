package fr.gospeak.web.pages.orga.settings

import fr.gospeak.core.domain.Group.Settings
import fr.gospeak.core.services.slack.domain.SlackCredentials
import fr.gospeak.web.utils.Mappings._
import play.api.data.Form
import play.api.data.Forms._

object SettingsForms {
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

  val eventSettings: Form[Settings.Event] = Form(mapping(
    "defaultDescription" -> template
  )(Settings.Event.apply)(Settings.Event.unapply))
}
