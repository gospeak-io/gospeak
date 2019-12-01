package fr.gospeak.core.services.email

import cats.effect.IO
import fr.gospeak.core.services.email.EmailSrv.Email
import fr.gospeak.libs.scalautils.domain.Done
import fr.gospeak.libs.scalautils.domain.EmailAddress.Contact

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

}
