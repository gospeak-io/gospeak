package fr.gospeak.web

import com.mohiva.play.silhouette.crypto.{JcaCrypterSettings, JcaSignerSettings}
import com.typesafe.config.Config
import fr.gospeak.core.services.email.EmailConf
import fr.gospeak.core.services.meetup.MeetupConf
import fr.gospeak.core.services.storage.DatabaseConf
import fr.gospeak.core.services.upload.UploadConf
import fr.gospeak.core.{ApplicationConf, GospeakConf}
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
                         email: EmailConf,
                         upload: UploadConf,
                         meetup: MeetupConf,
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
      case "local" => Right(ApplicationConf.Env.Local)
      case "dev" => Right(ApplicationConf.Env.Dev)
      case "prod" => Right(ApplicationConf.Env.Prod)
      case _ => Left(ConfigReaderFailures(ConvertFailure(CannotConvert(cur.toString, "ApplicationConf.Env", "Invalid value"), cur.location, cur.path)))
    }

    private implicit val sameSiteReader: ConfigReader[SameSite] = ConfigReader.fromString[SameSite](str => SameSite.parse(str).toEither(CannotConvert(str, "SameSite", s"possible values: '${SameSite.Strict.value}' or '${SameSite.Lax.value}'")))
    private implicit val authConfCookieSettingsReader: ConfigReader[AuthConf.CookieSettings] = deriveReader[AuthConf.CookieSettings]
    private implicit val jcaSignerSettingsReader: ConfigReader[JcaSignerSettings] = deriveReader[JcaSignerSettings]
    private implicit val jcaCrypterSettingsReader: ConfigReader[JcaCrypterSettings] = deriveReader[JcaCrypterSettings]
    private implicit val authConfRememberMeReader: ConfigReader[AuthConf.RememberMe] = deriveReader[AuthConf.RememberMe]
    private implicit val authConfCookieConfReader: ConfigReader[AuthConf.CookieConf] = deriveReader[AuthConf.CookieConf]
    private implicit val facebookConfReader: ConfigReader[AuthConf.FacebookConf] = deriveReader[AuthConf.FacebookConf]
    private implicit val githubConfReader: ConfigReader[AuthConf.GithubConf] = deriveReader[AuthConf.GithubConf]
    private implicit val googleConfReader: ConfigReader[AuthConf.GoogleConf] = deriveReader[AuthConf.GoogleConf]
    private implicit val linkedinConfReader: ConfigReader[AuthConf.LinkedinConf] = deriveReader[AuthConf.LinkedinConf]
    private implicit val twitterConfReader: ConfigReader[AuthConf.TwitterConf] = deriveReader[AuthConf.TwitterConf]

    private implicit val databaseConfReader: ConfigReader[DatabaseConf] = (cur: ConfigCursor) => cur.asString
      .flatMap(DatabaseConf.from(_).leftMap(_ => ConfigReaderFailures(ConvertFailure(CannotConvert(cur.toString, "DatabaseConf", "Invalid value"), cur.location, cur.path))))

    private implicit val emailConfConsoleReader: ConfigReader[EmailConf.Console] = deriveReader[EmailConf.Console]
    private implicit val emailConfInMemoryReader: ConfigReader[EmailConf.InMemory] = deriveReader[EmailConf.InMemory]
    private implicit val emailConfSendGridReader: ConfigReader[EmailConf.SendGrid] = deriveReader[EmailConf.SendGrid]

    private implicit val uploadConfUrlReader: ConfigReader[UploadConf.Url] = deriveReader[UploadConf.Url]
    private implicit val uploadConfCloudinaryReader: ConfigReader[UploadConf.Cloudinary] = deriveReader[UploadConf.Cloudinary]

    private implicit def markdownTmplReader[A]: ConfigReader[MustacheMarkdownTmpl[A]] = (cur: ConfigCursor) => cur.asString.map(_.stripPrefix("\n")).map(MustacheMarkdownTmpl[A])

    private implicit val gospeakEventConfReader: ConfigReader[GospeakConf.EventConf] = deriveReader[GospeakConf.EventConf]

    private implicit val applicationConfReader: ConfigReader[ApplicationConf] = deriveReader[ApplicationConf]
    private implicit val authConfReader: ConfigReader[AuthConf] = deriveReader[AuthConf]
    private implicit val emailConfReader: ConfigReader[EmailConf] = deriveReader[EmailConf]
    private implicit val uploadConfReader: ConfigReader[UploadConf] = deriveReader[UploadConf]
    private implicit val meetupClientConfReader: ConfigReader[MeetupConf] = deriveReader[MeetupConf]
    private implicit val gospeakConfReader: ConfigReader[GospeakConf] = deriveReader[GospeakConf]

    private implicit val appConfReader: ConfigReader[AppConf] = deriveReader[AppConf]

    val reader: Derivation[ConfigReader[AppConf]] = implicitly[Derivation[ConfigReader[AppConf]]]
  }

}
