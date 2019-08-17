package fr.gospeak.core.services.slack.domain

import fr.gospeak.core.domain.utils.TemplateData
import fr.gospeak.libs.scalautils.domain.MustacheTmpl.MustacheMarkdownTmpl

sealed trait SlackAction

object SlackAction {

  final case class PostMessage(channel: MustacheMarkdownTmpl[TemplateData],
                               message: MustacheMarkdownTmpl[TemplateData],
                               createdChannelIfNotExist: Boolean,
                               inviteEverybody: Boolean) extends SlackAction

}
