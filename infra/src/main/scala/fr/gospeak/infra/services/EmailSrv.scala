package fr.gospeak.infra.services

import cats.effect.IO
import fr.gospeak.infra.services.EmailSrv._
import fr.gospeak.libs.scalautils.Extensions._
import fr.gospeak.libs.scalautils.domain.EmailAddress.Contact
import fr.gospeak.libs.scalautils.domain.{Done, Secret}

import scala.collection.mutable
import scala.collection.mutable.ArrayBuffer

trait EmailSrv {
  def send(email: Email): IO[Done]
}

object EmailSrv {

  final case class Email(from: Contact, to: Seq[Contact], subject: String, content: Content)

  sealed trait Content {
    def value: String
  }

  final case class TextContent(text: String) extends Content {
    override def value: String = text
  }

  final case class HtmlContent(html: String) extends Content {
    override def value: String = html
  }

  sealed trait Conf

  object Conf {

    final case class Console() extends Conf

    final case class InMemory() extends Conf

    final case class SendGrid(apiKey: Secret) extends Conf

  }

  def from(conf: Conf): EmailSrv = conf match {
    case _: Conf.Console => new ConsoleEmailSrv()
    case _: Conf.InMemory => new InMemoryEmailSrv()
    case conf: Conf.SendGrid => SendGridEmailSrv(conf)
  }

}

// useful for dev
class ConsoleEmailSrv extends EmailSrv {
  override def send(email: Email): IO[Done] = IO {
    println(
      s"""EmailSrv.send(
         |  from: ${email.from.format},
         |  to: ${email.to.map(_.format).mkString(", ")},
         |  subject: ${email.subject},
         |  content:
         |${email.content.value}
         |)""".stripMargin)
    Done
  }
}

// useful for tests
class InMemoryEmailSrv extends EmailSrv {
  val sentEmails: ArrayBuffer[Email] = mutable.ArrayBuffer[Email]()

  override def send(email: Email): IO[Done] = IO {
    sentEmails.prepend(email)
    Done
  }
}

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
      _ <- IO(client.api(request)).filter(_.getStatusCode == 202)
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
  def apply(conf: Conf.SendGrid): SendGridEmailSrv =
    new SendGridEmailSrv(new com.sendgrid.SendGrid(conf.apiKey.decode))
}
