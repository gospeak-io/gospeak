package fr.gospeak.infra.services

import fr.gospeak.infra.services.EmailSrv.Conf.SendGrid
import fr.gospeak.infra.services.EmailSrv._
import fr.gospeak.libs.scalautils.Extensions._
import fr.gospeak.libs.scalautils.domain.{EmailAddress, Secret}
import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers

class EmailSrvSpec extends AnyFunSpec with Matchers {
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
