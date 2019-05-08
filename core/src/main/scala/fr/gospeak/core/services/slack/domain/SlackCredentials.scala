package fr.gospeak.core.services.slack.domain

final case class SlackCredentials(token: SlackToken,
                                  name: String,
                                  avatar: Option[String])
