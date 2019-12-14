package fr.gospeak.core.services.email

import fr.gospeak.libs.scalautils.domain.Secret

sealed trait EmailConf

object EmailConf {

  final case class Console() extends EmailConf

  final case class InMemory() extends EmailConf

  final case class SendGrid(apiKey: Secret) extends EmailConf

}
