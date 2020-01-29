package gospeak.core.services.slack.domain

import gospeak.core.domain.utils.TemplateData
import gospeak.libs.scala.domain.MustacheTmpl.MustacheMarkdownTmpl

sealed trait SlackAction

object SlackAction {

  final case class PostMessage(channel: MustacheMarkdownTmpl[TemplateData],
                               message: MustacheMarkdownTmpl[TemplateData],
                               createdChannelIfNotExist: Boolean,
                               inviteEverybody: Boolean) extends SlackAction

}
