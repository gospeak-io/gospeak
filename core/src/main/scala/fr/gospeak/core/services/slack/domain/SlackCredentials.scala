package fr.gospeak.core.services.slack.domain

import fr.gospeak.libs.scalautils.domain.Avatar

final case class SlackCredentials(token: SlackToken,
                                  name: String,
                                  avatar: Option[Avatar])
