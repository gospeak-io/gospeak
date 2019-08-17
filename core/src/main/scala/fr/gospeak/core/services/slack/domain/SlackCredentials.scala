package fr.gospeak.core.services.slack.domain

import fr.gospeak.libs.scalautils.domain.Url

final case class SlackCredentials(token: SlackToken, // FIXME must be encoded
                                  name: String,
                                  avatar: Option[Url])
