package fr.gospeak.infra.services.email

import fr.gospeak.core.services.email.EmailSrv

object EmailSrvFactory {
  def from(conf: EmailSrvConf): EmailSrv = conf match {
    case _: EmailSrvConf.Console => new ConsoleEmailSrv()
    case _: EmailSrvConf.InMemory => new InMemoryEmailSrv()
    case conf: EmailSrvConf.SendGrid => SendGridEmailSrv(conf)
  }
}
