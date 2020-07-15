package gospeak.core.services.slack.domain

import gospeak.libs.scala.domain.{Liquid, LiquidMarkdown}

sealed trait SlackAction

object SlackAction {

  final case class PostMessage(channel: Liquid[Any],
                               message: LiquidMarkdown[Any],
                               createdChannelIfNotExist: Boolean,
                               inviteEverybody: Boolean) extends SlackAction

}
