package fr.gospeak.core.services.meetup

import fr.gospeak.libs.scalautils.domain.Secret

final case class MeetupConf(key: String, secret: Secret)
