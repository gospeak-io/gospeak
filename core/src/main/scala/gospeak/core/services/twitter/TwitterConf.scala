package gospeak.core.services.twitter

import gospeak.libs.scala.domain.Secret

sealed trait TwitterConf

object TwitterConf {

  final case class Console() extends TwitterConf

  final case class Twitter(consumerKey: String,
                           consumerSecret: Secret,
                           accessKey: String,
                           accessSecret: Secret) extends TwitterConf

}
