package gospeak.core.services.slack.domain

import gospeak.libs.scala.domain.{Mustache, MustacheMarkdown}

sealed trait SlackAction

object SlackAction {

  final case class PostMessage(channel: Mustache[Any],
                               message: MustacheMarkdown[Any],
                               createdChannelIfNotExist: Boolean,
                               inviteEverybody: Boolean) extends SlackAction

}
