package fr.gospeak.core.services.slack.domain

import fr.gospeak.libs.scalautils.domain.MarkdownTemplate

sealed trait SlackAction

object SlackAction {

  final case class PostMessage(channel: MarkdownTemplate,
                               message: MarkdownTemplate,
                               createdChannelIfNotExist: Boolean,
                               inviteEverybody: Boolean) extends SlackAction

}
