package fr.gospeak.core.services.matomo

import fr.gospeak.libs.scalautils.domain.Secret

final case class MatomoConf(baseUrl: String,
                            site: Int,
                            token: Secret)
