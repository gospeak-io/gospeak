package fr.gospeak.core.services.slack.domain

import fr.gospeak.libs.scalautils.domain.Template

sealed trait SlackAction

object SlackAction {

  final case class PostMessage(channel: Template,
                               message: Template) extends SlackAction

}
