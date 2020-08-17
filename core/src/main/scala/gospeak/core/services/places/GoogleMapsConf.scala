package gospeak.core.services.places

import gospeak.libs.scala.domain.Secret

final case class GoogleMapsConf(backendApiKey: Secret,
                                frontendApiKey: Secret)
