package gospeak.infra.services.email

import cats.effect.IO
import gospeak.core.services.email.EmailSrv
import gospeak.core.services.email.EmailSrv.Email
import gospeak.libs.scala.domain.Done
import org.slf4j.LoggerFactory

// useful for dev
class ConsoleEmailSrv extends EmailSrv {
  private val logger = LoggerFactory.getLogger(this.getClass)

  override def send(email: Email): IO[Done] = IO {
    logger.info(
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
