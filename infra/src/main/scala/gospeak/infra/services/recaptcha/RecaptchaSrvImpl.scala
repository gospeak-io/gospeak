package gospeak.infra.services.recaptcha

import cats.effect.IO
import gospeak.core.services.recaptcha.{RecaptchaConf, RecaptchaSrv}
import gospeak.infra.services.recaptcha.RecaptchaSrvImpl.Response
import gospeak.libs.http.HttpClient
import gospeak.libs.scala.domain.{CustomException, Secret}
import io.circe
import io.circe.parser.decode
import io.circe.{Decoder, HCursor}

import java.time.Instant

class RecaptchaSrvImpl(conf: RecaptchaConf, http: HttpClient) extends RecaptchaSrv {
  private val verifyUrl = "https://www.google.com/recaptcha/api/siteverify"

  def validate(response: Option[Secret]): IO[Unit] = conf match {
    case _: RecaptchaConf.Disabled => IO.pure(())
    case c: RecaptchaConf.Enabled => response match {
      case None => IO.raiseError(CustomException("Missing reCAPTCHA challenge"))
      case Some(value) => verify(c.serverKey.decode, value.decode).flatMap {
        case Left(err) => IO.raiseError(CustomException(s"Can't decode reCAPTCHA response: ${err.getMessage.replace("Attempt to decode value on failed cursor: ", "")}"))
        case Right(res) => if (res.success) IO.pure(()) else IO.raiseError(CustomException("Failed reCAPTCHA challenge"))
      }
    }
  }

  private[recaptcha] def verify(secret: String, response: String): IO[Either[circe.Error, Response]] =
    http.post(verifyUrl, query = Map("secret" -> secret, "response" -> response)).map(r => parse(r.body))

  private[recaptcha] def parse(json: String): Either[circe.Error, Response] = decode[Response](json)
}

object RecaptchaSrvImpl {
  case class Response(success: Boolean,
                      challengeTs: Option[Instant],
                      hostname: Option[String],
                      errorCodes: List[String])


  implicit val responseDecoder: Decoder[Response] = (c: HCursor) => for {
    success <- c.downField("success").as[Boolean]
    challengeTs <- c.downField("challenge_ts").as[Option[Instant]]
    hostname <- c.downField("hostname").as[Option[String]]
    errorCodes <- c.downField("error-codes").as[Option[List[String]]]
  } yield Response(success, challengeTs, hostname, errorCodes.getOrElse(List()))
}
