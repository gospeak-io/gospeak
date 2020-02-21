package gospeak.infra.services

import gospeak.core.services.email.EmailConf.SendGrid
import gospeak.core.services.email.EmailSrv._
import gospeak.infra.services.email.SendGridEmailSrv
import gospeak.libs.scala.Extensions._
import gospeak.libs.scala.domain.{EmailAddress, Secret}
import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers

class EmailSrvSpec extends AnyFunSpec with Matchers {
  private val sender = EmailAddress.from("unit-test@gospeak.io").get
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
