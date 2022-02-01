package gospeak.infra.services.email

import cats.effect.IO
import gospeak.core.services.email.EmailSrv.{Email, HtmlContent, TextContent}
import gospeak.core.services.email.{EmailConf, EmailSrv}
import gospeak.libs.scala.Extensions._
import gospeak.libs.scala.domain.Done
import gospeak.libs.scala.domain.EmailAddress.Contact

class SendGridEmailSrv private(client: com.sendgrid.SendGrid) extends EmailSrv {

  import com.sendgrid.helpers.mail.{objects => sgO}
  import com.sendgrid.helpers.{mail => sgM}
  import com.{sendgrid => sg}

  override def send(email: Email): IO[Done] = {
    // https://github.com/sendgrid/sendgrid-java/blob/master/examples/helpers/mail/Example.java#L30
    val mail = buildMail(email)
    val request = new sg.Request()
    request.setMethod(sg.Method.POST)
    request.setEndpoint("mail/send")
    for {
      body <- IO(mail.build)
      _ = request.setBody(body)
      _ <- IO(client.api(request)).filterErr(_.getStatusCode == 202, r => s"Expected status 202 but got ${r.getStatusCode} (${r.getBody})")
    } yield Done
  }

  private def buildMail(email: Email): sgM.Mail = {
    val mail = new sgM.Mail()
    mail.setFrom(buildEmail(email.from))
    val personalization = new sgO.Personalization()
    email.to.foreach(to => personalization.addTo(buildEmail(to)))
    mail.addPersonalization(personalization)
    mail.setSubject(email.subject)
    email.content match {
      case TextContent(text) => mail.addContent(new sgO.Content("text/plain", text))
      case HtmlContent(html) => mail.addContent(new sgO.Content("text/html", html))
    }
    mail
  }

  private def buildEmail(contact: Contact): sgO.Email =
    contact.name.map { name =>
      new sgO.Email(contact.address.value, name)
    }.getOrElse {
      new sgO.Email(contact.address.value)
    }
}

object SendGridEmailSrv {
  def apply(conf: EmailConf.SendGrid): SendGridEmailSrv =
    new SendGridEmailSrv(new com.sendgrid.SendGrid(conf.apiKey.decode))
}
