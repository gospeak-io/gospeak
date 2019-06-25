package fr.gospeak.web

import com.mohiva.play.silhouette.crypto.{JcaCrypterSettings, JcaSignerSettings}
import com.typesafe.config.{Config, ConfigFactory}
import fr.gospeak.infra.services.EmailSrv
import fr.gospeak.infra.services.storage.sql.DatabaseConf
import fr.gospeak.libs.scalautils.Extensions._
import fr.gospeak.libs.scalautils.domain.Secret
import fr.gospeak.web.auth.AuthConf
import play.api.Configuration
import play.api.mvc.Cookie.SameSite
import pureconfig.error.{CannotConvert, ConfigReaderFailure, ConfigReaderFailures, ConvertFailure}
import pureconfig.generic.EnumCoproductHint
import pureconfig.{ConfigCursor, ConfigReader}

import scala.util.{Failure, Success, Try}

final case class AppConf(application: ApplicationConf,
                         auth: AuthConf,
                         database: DatabaseConf,
                         emailService: EmailSrv.Conf)

object AppConf {
  def load(conf: Config): Try[AppConf] = {
    import pureconfig.generic.semiauto._

    implicit val secretConverter: ConfigReader[Secret] = deriveReader[Secret]

    implicit val applicationConfEnvConverter: ConfigReader[ApplicationConf.Env] = (cur: ConfigCursor) => cur.asString.flatMap {
      case "local" => Right(ApplicationConf.Local)
      case "dev" => Right(ApplicationConf.Dev)
      case "prod" => Right(ApplicationConf.Prod)
      case _ => Left(ConfigReaderFailures(ConvertFailure(CannotConvert(cur.toString, "ApplicationConf.Env", "Invalid value"), cur.location, cur.path)))
    }

    implicit val sameSiteReader: ConfigReader[SameSite] = ConfigReader.fromString[SameSite](str => SameSite.parse(str).toEither(CannotConvert(str, "SameSite", s"possible values: '${SameSite.Strict.value}' or '${SameSite.Lax.value}'")))
    implicit val authConfCookieSettingsConverter: ConfigReader[AuthConf.CookieSettings] = deriveReader[AuthConf.CookieSettings]
    implicit val jcaSignerSettingsConverter: ConfigReader[JcaSignerSettings] = deriveReader[JcaSignerSettings]
    implicit val jcaCrypterSettingsConverter: ConfigReader[JcaCrypterSettings] = deriveReader[JcaCrypterSettings]
    implicit val authConfRememberMeConverter: ConfigReader[AuthConf.RememberMe] = deriveReader[AuthConf.RememberMe]
    implicit val authConfCookieConfConverter: ConfigReader[AuthConf.CookieConf] = deriveReader[AuthConf.CookieConf]

    implicit val databaseConfH2Converter: ConfigReader[DatabaseConf.H2] = deriveReader[DatabaseConf.H2]
    implicit val databaseConfPostgreSQLConverter: ConfigReader[DatabaseConf.PostgreSQL] = deriveReader[DatabaseConf.PostgreSQL]

    implicit val emailSrvConfConsoleConverter: ConfigReader[EmailSrv.Conf.Console] = deriveReader[EmailSrv.Conf.Console]
    implicit val emailSrvConfInMemeryConverter: ConfigReader[EmailSrv.Conf.InMemery] = deriveReader[EmailSrv.Conf.InMemery]
    implicit val emailSrvConfSendGridConverter: ConfigReader[EmailSrv.Conf.SendGrid] = deriveReader[EmailSrv.Conf.SendGrid]

    implicit val applicationConfConverter: ConfigReader[ApplicationConf] = deriveReader[ApplicationConf]
    implicit val authConfConverter: ConfigReader[AuthConf] = deriveReader[AuthConf]
    implicit val databaseConfConverter: ConfigReader[DatabaseConf] = deriveReader[DatabaseConf]
    implicit val appConfConverter: ConfigReader[EmailSrv.Conf] = deriveReader[EmailSrv.Conf]

    implicit val emailSrvConfConverter: ConfigReader[AppConf] = deriveReader[AppConf]

    pureconfig.loadConfig[AppConf](conf) match {
      case Right(appConf) => Success(appConf)
      case Left(failures) => Failure(new IllegalArgumentException("Unable to load AppConf:\n" + failures.toList.map(format).mkString("\n")))
    }
  }

  def load(conf: Configuration): Try[AppConf] =
    load(conf.underlying)

  def load(): Try[AppConf] = load(ConfigFactory.load())

  private def format(f: ConfigReaderFailure): String =
    "  - " + f.description + f.location.map(" " + _.description).getOrElse("")
}

final case class ApplicationConf(env: ApplicationConf.Env)

object ApplicationConf {

  sealed trait Env {
    def isProd: Boolean = false
  }

  object Env {
    implicit val hint: EnumCoproductHint[Env] = new EnumCoproductHint[Env]
  }

  final case object Local extends Env

  final case object Dev extends Env

  final case object Prod extends Env {
    override def isProd: Boolean = true
  }

}
