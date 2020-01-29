package gospeak.core.services.email

import gospeak.libs.scala.domain.Secret

sealed trait EmailConf

object EmailConf {

  final case class Console() extends EmailConf

  final case class InMemory() extends EmailConf

  final case class SendGrid(apiKey: Secret) extends EmailConf

}
