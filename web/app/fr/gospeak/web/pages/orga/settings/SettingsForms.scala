package fr.gospeak.web.pages.orga.settings

import fr.gospeak.core.domain.Group.Settings.Action
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

  final case class AddAction(trigger: Action.Trigger, action: Action)

  val addAction: Form[AddAction] = Form(mapping(
    "trigger" -> groupSettingsEvent,
    "action" -> groupSettingsAction
  )(AddAction.apply)(AddAction.unapply))
}
