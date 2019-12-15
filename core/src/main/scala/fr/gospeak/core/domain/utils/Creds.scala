package fr.gospeak.core.domain.utils

import fr.gospeak.libs.scalautils.domain.Secret

final case class Creds(key: String, secret: Secret)
