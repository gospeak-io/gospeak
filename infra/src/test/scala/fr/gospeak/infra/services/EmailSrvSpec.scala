package fr.gospeak.infra.services

import fr.gospeak.core.services.email.EmailSrv._
import fr.gospeak.infra.services.email.EmailSrvConf.SendGrid
import fr.gospeak.infra.services.email.SendGridEmailSrv
import fr.gospeak.libs.scalautils.Extensions._
import fr.gospeak.libs.scalautils.domain.{EmailAddress, Secret}
import org.scalatest.{FunSpec, Matchers}

class EmailSrvSpec extends FunSpec with Matchers {
  private val sender = EmailAddress.from("unit-test@gospeak.fr").get
  private val receiver = EmailAddress.from("loicknuchel@gmail.com").get

  ignore("SendGridEmailSrv") {
    it("should send an email") {
      val conf = SendGrid(Secret("..."))
      val srv = SendGridEmailSrv.apply(conf)
      srv.send(Email(
        from = EmailAddress.Contact(sender, Some("Gospeak unit tests")),
        to = Seq(EmailAddress.Contact(receiver, Some("LKN"))),
        subject = "SendGrid test!!",
        content = HtmlContent("This is a <b>test</b> to send SendGrid email :D"))).unsafeRunSync()
    }
  }
}
