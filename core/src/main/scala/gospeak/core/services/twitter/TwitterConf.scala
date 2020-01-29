package gospeak.core.services.twitter

import gospeak.libs.scala.domain.Secret

final case class TwitterConf(consumerKey: String,
                             consumerSecret: Secret,
                             accessKey: String,
                             accessSecret: Secret)
