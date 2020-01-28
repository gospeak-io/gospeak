package gospeak.web.pages.orga.settings

import gospeak.core.domain.Group.Settings
import gospeak.core.domain.utils.TemplateData
import gospeak.core.services.meetup.domain.MeetupGroup
import gospeak.core.services.slack.domain.SlackCredentials
import gospeak.libs.scala.Crypto.AesSecretKey
import gospeak.libs.scala.domain.MustacheTmpl.MustacheMarkdownTmpl
import gospeak.web.utils.Mappings._
import play.api.data.Form
import play.api.data.Forms._

object SettingsForms {

  final case class MeetupAccount(group: MeetupGroup.Slug)

  val meetupAccount: Form[MeetupAccount] = Form(mapping(
    "group" -> meetupGroupSlug
  )(MeetupAccount.apply)(MeetupAccount.unapply))

  def slackAccount(key: AesSecretKey): Form[SlackCredentials] = Form(mapping(
    "token" -> slackToken(key),
    "name" -> nonEmptyText,
    "avatar" -> optional(avatar)
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
