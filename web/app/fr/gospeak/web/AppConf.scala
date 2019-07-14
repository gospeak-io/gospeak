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
import pureconfig.{ConfigCursor, ConfigReader, Derivation}

import scala.util.{Failure, Success, Try}

final case class AppConf(application: ApplicationConf,
                         auth: AuthConf,
                         database: DatabaseConf,
                         emailService: EmailSrv.Conf)

object AppConf {
  def load(conf: Config): Try[AppConf] = {
    pureconfig.loadConfig[AppConf](conf)(Readers.reader) match {
      case Right(appConf) => Success(appConf)
      case Left(failures) => Failure(new IllegalArgumentException("Unable to load AppConf:\n" + failures.toList.map(format).mkString("\n")))
    }
  }

  def load(conf: Configuration): Try[AppConf] =
    load(conf.underlying)

  def load(): Try[AppConf] = load(ConfigFactory.load())

  private def format(f: ConfigReaderFailure): String =
    "  - " + f.description + f.location.map(" " + _.description).getOrElse("")

  object Readers {

    import pureconfig.generic.semiauto._

    private implicit val secretReader: ConfigReader[Secret] = deriveReader[Secret]

    private implicit val applicationConfEnvReader: ConfigReader[ApplicationConf.Env] = (cur: ConfigCursor) => cur.asString.flatMap {
      case "local" => Right(ApplicationConf.Local)
      case "dev" => Right(ApplicationConf.Dev)
      case "prod" => Right(ApplicationConf.Prod)
      case _ => Left(ConfigReaderFailures(ConvertFailure(CannotConvert(cur.toString, "ApplicationConf.Env", "Invalid value"), cur.location, cur.path)))
    }

    private implicit val sameSiteReader: ConfigReader[SameSite] = ConfigReader.fromString[SameSite](str => SameSite.parse(str).toEither(CannotConvert(str, "SameSite", s"possible values: '${SameSite.Strict.value}' or '${SameSite.Lax.value}'")))
    private implicit val authConfCookieSettingsReader: ConfigReader[AuthConf.CookieSettings] = deriveReader[AuthConf.CookieSettings]
    private implicit val jcaSignerSettingsReader: ConfigReader[JcaSignerSettings] = deriveReader[JcaSignerSettings]
    private implicit val jcaCrypterSettingsReader: ConfigReader[JcaCrypterSettings] = deriveReader[JcaCrypterSettings]
    private implicit val authConfRememberMeReader: ConfigReader[AuthConf.RememberMe] = deriveReader[AuthConf.RememberMe]
    private implicit val authConfCookieConfReader: ConfigReader[AuthConf.CookieConf] = deriveReader[AuthConf.CookieConf]

    private implicit val databaseConfH2Reader: ConfigReader[DatabaseConf.H2] = deriveReader[DatabaseConf.H2]
    private implicit val databaseConfPostgreSQLReader: ConfigReader[DatabaseConf.PostgreSQL] = deriveReader[DatabaseConf.PostgreSQL]

    private implicit val emailSrvConfConsoleReader: ConfigReader[EmailSrv.Conf.Console] = deriveReader[EmailSrv.Conf.Console]
    private implicit val emailSrvConfInMemeryReader: ConfigReader[EmailSrv.Conf.InMemery] = deriveReader[EmailSrv.Conf.InMemery]
    private implicit val emailSrvConfSendGridReader: ConfigReader[EmailSrv.Conf.SendGrid] = deriveReader[EmailSrv.Conf.SendGrid]

    private implicit val applicationConfReader: ConfigReader[ApplicationConf] = deriveReader[ApplicationConf]
    private implicit val authConfReader: ConfigReader[AuthConf] = deriveReader[AuthConf]
    private implicit val databaseConfReader: ConfigReader[DatabaseConf] = deriveReader[DatabaseConf]
    private implicit val emailSrvConfReader: ConfigReader[EmailSrv.Conf] = deriveReader[EmailSrv.Conf]

    private implicit val appConfReader: ConfigReader[AppConf] = deriveReader[AppConf]

    val reader: Derivation[ConfigReader[AppConf]] = implicitly[Derivation[ConfigReader[AppConf]]]
  }

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
