package gospeak.infra.services.email

import gospeak.core.services.email.EmailConf.SendGrid
import gospeak.core.services.email.EmailSrv._
import gospeak.infra.testingutils.BaseSpec
import gospeak.libs.scala.Extensions._
import gospeak.libs.scala.domain.{EmailAddress, Secret}

class SendGridEmailSrvSpec extends BaseSpec {
  private val sender = EmailAddress.from("unit-test@gospeak.io").get
  private val receiver = EmailAddress.from("loicknuchel@gmail.com").get

  ignore("SendGridEmailSrv") {
    it("should send an email") {
      val conf = SendGrid(Secret("..."))
      val srv = SendGridEmailSrv.apply(conf)
      srv.send(Email(
        from = EmailAddress.Contact(sender, Some("Gospeak unit tests")),
        to = List(EmailAddress.Contact(receiver, Some("LKN"))),
        subject = "SendGrid test!!",
        content = HtmlContent("This is a <b>test</b> to send SendGrid email :D"))).unsafeRunSync()
    }
  }
}
