package gospeak.core.services.slack.domain

import gospeak.libs.scala.domain.Avatar

final case class SlackCredentials(token: SlackToken,
                                  name: String,
                                  avatar: Option[Avatar])
