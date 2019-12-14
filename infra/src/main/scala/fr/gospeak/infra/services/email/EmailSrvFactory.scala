package fr.gospeak.infra.services.email

import fr.gospeak.core.services.email.{EmailSrv, EmailConf}

object EmailSrvFactory {
  def from(conf: EmailConf): EmailSrv = conf match {
    case _: EmailConf.Console => new ConsoleEmailSrv()
    case _: EmailConf.InMemory => new InMemoryEmailSrv()
    case conf: EmailConf.SendGrid => SendGridEmailSrv(conf)
  }
}
