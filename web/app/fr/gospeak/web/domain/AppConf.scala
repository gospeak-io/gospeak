package fr.gospeak.web.domain

import com.mohiva.play.silhouette.crypto.{JcaCrypterSettings, JcaSignerSettings}
import com.mohiva.play.silhouette.impl.authenticators.CookieAuthenticatorSettings
import com.typesafe.config.{Config, ConfigFactory}
import fr.gospeak.libs.scalautils.Extensions._
import play.api.Configuration
import play.api.mvc.Cookie.SameSite
import pureconfig.ConfigReader
import pureconfig.error.{CannotConvert, ConfigReaderFailure}

import scala.concurrent.duration.FiniteDuration
import scala.util.{Failure, Success, Try}

final case class AppConf(auth: AuthConf)

object AppConf {
  def load(conf: Config): Try[AppConf] = {
    import pureconfig.generic.auto._
    implicit val sameSiteReader: ConfigReader[SameSite] = ConfigReader.fromString[SameSite](str => SameSite.parse(str).toEither(CannotConvert(str, "SameSite", s"possible values: '${SameSite.Strict.value}' or '${SameSite.Lax.value}'")))
    val _ = exportReader[CookieAuthenticatorSettings] // to prevent IntelliJ from removing import
    pureconfig.loadConfig[AppConf](conf) match {
      case Right(appConf) => Success(appConf)
      case Left(failures) => Failure(new IllegalArgumentException("Unable to load AppConf:\n" + failures.toList.map(format).mkString("\n")))
    }
  }

  def load(conf: Configuration): Try[AppConf] =
    load(conf.underlying)

  def load(): Try[AppConf] =
    load(ConfigFactory.load())

  private def format(f: ConfigReaderFailure): String =
    "  - " + f.description + f.location.map(" " + _.description).getOrElse("")
}

final case class AuthConf(cookie: AuthCookieConf)

final case class AuthCookieConf(authenticator: CookieAuthenticatorSettings,
                                signer: JcaSignerSettings,
                                crypter: JcaCrypterSettings,
                                rememberMe: RememberMe)

final case class RememberMe(cookieMaxAge: FiniteDuration,
                            authenticatorIdleTimeout: FiniteDuration,
                            authenticatorExpiry: FiniteDuration)
