package gospeak.core.services.recaptcha

import gospeak.libs.scala.domain.Secret


sealed trait RecaptchaConf {
  def map[A](f: RecaptchaConf.Enabled => A): Option[A] = this match {
    case _: RecaptchaConf.Disabled => None
    case c: RecaptchaConf.Enabled => Some(f(c))
  }
}

object RecaptchaConf {

  final case class Disabled() extends RecaptchaConf

  final case class Enabled(clientKey: String,
                           serverKey: Secret) extends RecaptchaConf

}
