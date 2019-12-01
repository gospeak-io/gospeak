package fr.gospeak.infra.services.email

import fr.gospeak.libs.scalautils.domain.Secret

sealed trait EmailSrvConf

object EmailSrvConf {

  final case class Console() extends EmailSrvConf

  final case class InMemory() extends EmailSrvConf

  final case class SendGrid(apiKey: Secret) extends EmailSrvConf

}
