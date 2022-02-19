package gospeak.core.services.recaptcha

import cats.effect.IO
import gospeak.libs.scala.domain.Secret

trait RecaptchaSrv {
  def validate(secret: Option[Secret]): IO[Unit]
}
