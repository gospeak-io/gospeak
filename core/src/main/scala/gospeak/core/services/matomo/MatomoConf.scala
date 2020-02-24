package gospeak.core.services.matomo

import gospeak.libs.scala.domain.Secret

final case class MatomoConf(baseUrl: String,
                            site: Int,
                            token: Secret)
