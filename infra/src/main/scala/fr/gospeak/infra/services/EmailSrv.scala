package fr.gospeak.infra.services

import cats.effect.IO
import fr.gospeak.infra.services.EmailSrv._
import fr.gospeak.libs.scalautils.Extensions._
import fr.gospeak.libs.scalautils.domain.Secret

trait EmailSrv {
  def send(email: Email): IO[Unit]
}

object EmailSrv {

  final case class Email(from: Contact, to: Seq[Contact], subject: String, content: Content)

  final case class Contact(mail: String, name: Option[String])

  sealed trait Content

  final case class TextContent(text: String) extends Content

  final case class HtmlContent(html: String) extends Content

}

class ConsoleEmailSrv extends EmailSrv {
  override def send(email: Email): IO[Unit] =
    IO(println(s"EmailSrv.send(from: ${email.from.mail}, to: ${email.to.map(_.mail).mkString(",")}, subject: ${email.subject}, content: ${email.content})"))
}

object ConsoleEmailSrv {
  def apply(): ConsoleEmailSrv = new ConsoleEmailSrv()
}

class SendGridEmailSrv private(client: com.sendgrid.SendGrid) extends EmailSrv {

  import com.{sendgrid => sg}

  override def send(email: Email): IO[Unit] = {
    // https://github.com/sendgrid/sendgrid-java/blob/master/examples/helpers/mail/Example.java#L30
    val mail = buildMail(email)
    val request = new sg.Request()
    request.setMethod(sg.Method.POST)
    request.setEndpoint("mail/send")
    for {
      body <- IO(mail.build)
      _ = request.setBody(body)
      _ <- IO(client.api(request)).filter(_.getStatusCode == 202)
    } yield ()
  }

  private def buildMail(email: Email): sg.Mail = {
    val mail = new sg.Mail()
    mail.setFrom(buildEmail(email.from))
    val personalization = new sg.Personalization()
    email.to.foreach(to => personalization.addTo(buildEmail(to)))
    mail.addPersonalization(personalization)
    mail.setSubject(email.subject)
    email.content match {
      case TextContent(text) => mail.addContent(new sg.Content("text/plain", text))
      case HtmlContent(html) => mail.addContent(new sg.Content("text/html", html))
    }
    mail
  }

  private def buildEmail(contact: Contact): sg.Email =
    new sg.Email(contact.mail)
}

object SendGridEmailSrv {

  final case class APiKeyConf(key: Secret)

  def apply(conf: APiKeyConf): SendGridEmailSrv =
    new SendGridEmailSrv(new com.sendgrid.SendGrid(conf.key.decode))
}
