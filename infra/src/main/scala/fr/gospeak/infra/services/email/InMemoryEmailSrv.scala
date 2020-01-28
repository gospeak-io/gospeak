package fr.gospeak.infra.services.email

import cats.effect.IO
import fr.gospeak.core.services.email.EmailSrv
import fr.gospeak.core.services.email.EmailSrv.Email
import gospeak.libs.scala.domain.Done

import scala.collection.mutable
import scala.collection.mutable.ArrayBuffer

// useful for tests
class InMemoryEmailSrv extends EmailSrv {
  val sentEmails: ArrayBuffer[Email] = mutable.ArrayBuffer[Email]()

  override def send(email: Email): IO[Done] = IO {
    sentEmails.prepend(email)
    Done
  }
}
