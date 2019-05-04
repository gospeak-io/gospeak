package fr.gospeak.core.services.slack.domain

sealed trait SlackAction {
  val enabled: Boolean
}

object SlackAction {

  final case class PostMessage(enabled: Boolean,
                               channel: SlackChannel.Id,
                               contentTmpl: String) extends SlackAction

}
