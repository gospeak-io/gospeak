package fr.gospeak.web

import com.mohiva.play.silhouette.crypto.{JcaCrypterSettings, JcaSignerSettings}
import com.mohiva.play.silhouette.impl.providers.{OAuth1Settings, OAuth2Settings}
import com.typesafe.config.Config
import fr.gospeak.core.{ApplicationConf, GospeakConf}
import fr.gospeak.infra.libs.meetup.MeetupClient
import fr.gospeak.infra.services.EmailSrv
import fr.gospeak.infra.services.storage.sql.DatabaseConf
import fr.gospeak.libs.scalautils.Crypto.AesSecretKey
import fr.gospeak.libs.scalautils.Extensions._
import fr.gospeak.libs.scalautils.domain.MustacheTmpl.MustacheMarkdownTmpl
import fr.gospeak.libs.scalautils.domain.Secret
import fr.gospeak.web.auth.AuthConf
import play.api.Configuration
import play.api.mvc.Cookie.SameSite
import pureconfig.error.{CannotConvert, ConfigReaderFailure, ConfigReaderFailures, ConvertFailure}
import pureconfig.{ConfigCursor, ConfigReader, ConfigSource, Derivation}

import scala.util.{Failure, Success, Try}

final case class AppConf(application: ApplicationConf,
                         auth: AuthConf,
                         database: DatabaseConf,
                         emailService: EmailSrv.Conf,
                         meetup: MeetupClient.Conf,
                         gospeak: GospeakConf)

object AppConf {
  def load(conf: Config): Try[AppConf] = {
    ConfigSource.fromConfig(conf).load[AppConf](Readers.reader) match {
      case Right(appConf) => Success(appConf)
      case Left(failures) => Failure(new IllegalArgumentException("Unable to load AppConf:\n" + failures.toList.map(format).mkString("\n")))
    }
  }

  def load(conf: Configuration): Try[AppConf] = load(conf.underlying)

  private def format(f: ConfigReaderFailure): String = "  - " + f.description + f.location.map(" " + _.description).getOrElse("")

  private object Readers {

    import pureconfig.generic.semiauto._

    private implicit val secretReader: ConfigReader[Secret] = deriveReader[Secret]
    private implicit val aesSecretKeyReader: ConfigReader[AesSecretKey] = (cur: ConfigCursor) => cur.asString.map(AesSecretKey)

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
    private implicit val authOAuth2Settings: ConfigReader[OAuth2Settings] = deriveReader[OAuth2Settings]
    private implicit val authOAuth1Settings: ConfigReader[OAuth1Settings] = deriveReader[OAuth1Settings]

    private implicit val databaseConfReader: ConfigReader[DatabaseConf] = (cur: ConfigCursor) => cur.asString
      .flatMap(DatabaseConf.from(_).leftMap(_ => ConfigReaderFailures(ConvertFailure(CannotConvert(cur.toString, "DatabaseConf", "Invalid value"), cur.location, cur.path))))

    private implicit val emailSrvConfConsoleReader: ConfigReader[EmailSrv.Conf.Console] = deriveReader[EmailSrv.Conf.Console]
    private implicit val emailSrvConfInMemoryReader: ConfigReader[EmailSrv.Conf.InMemory] = deriveReader[EmailSrv.Conf.InMemory]
    private implicit val emailSrvConfSendGridReader: ConfigReader[EmailSrv.Conf.SendGrid] = deriveReader[EmailSrv.Conf.SendGrid]

    private implicit def markdownTmplReader[A]: ConfigReader[MustacheMarkdownTmpl[A]] = (cur: ConfigCursor) => cur.asString.map(_.stripPrefix("\n")).map(MustacheMarkdownTmpl[A])

    private implicit val gospeakEventConfReader: ConfigReader[GospeakConf.EventConf] = deriveReader[GospeakConf.EventConf]

    private implicit val applicationConfReader: ConfigReader[ApplicationConf] = deriveReader[ApplicationConf]
    private implicit val authConfReader: ConfigReader[AuthConf] = deriveReader[AuthConf]
    private implicit val meetupClientConfReader: ConfigReader[MeetupClient.Conf] = deriveReader[MeetupClient.Conf]
    private implicit val emailSrvConfReader: ConfigReader[EmailSrv.Conf] = deriveReader[EmailSrv.Conf]
    private implicit val gospeakConfReader: ConfigReader[GospeakConf] = deriveReader[GospeakConf]

    private implicit val appConfReader: ConfigReader[AppConf] = deriveReader[AppConf]

    val reader: Derivation[ConfigReader[AppConf]] = implicitly[Derivation[ConfigReader[AppConf]]]
  }

}
