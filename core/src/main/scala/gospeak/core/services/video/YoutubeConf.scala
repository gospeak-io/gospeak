package gospeak.core.services.video

import gospeak.libs.scala.domain.Secret

sealed trait YoutubeConf

object YoutubeConf {

  final case class Disabled() extends YoutubeConf

  final case class Enabled(secret: Secret) extends YoutubeConf

}
