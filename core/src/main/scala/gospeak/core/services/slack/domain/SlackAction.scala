package gospeak.core.services.slack.domain

import gospeak.libs.scala.domain.Mustache

sealed trait SlackAction

object SlackAction {

  final case class PostMessage(channel: Mustache.Text[Any],
                               message: Mustache.Markdown[Any],
                               createdChannelIfNotExist: Boolean,
                               inviteEverybody: Boolean) extends SlackAction

}
