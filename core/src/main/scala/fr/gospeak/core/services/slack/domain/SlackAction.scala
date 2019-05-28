package fr.gospeak.core.services.slack.domain

import fr.gospeak.core.domain.utils.TemplateData
import fr.gospeak.libs.scalautils.domain.MarkdownTemplate

sealed trait SlackAction

object SlackAction {

  final case class PostMessage(channel: MarkdownTemplate[TemplateData],
                               message: MarkdownTemplate[TemplateData],
                               createdChannelIfNotExist: Boolean,
                               inviteEverybody: Boolean) extends SlackAction

}
