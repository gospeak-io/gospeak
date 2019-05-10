package fr.gospeak.infra.libs.slack.domain

final case class SlackError(ok: Boolean, error: String)
