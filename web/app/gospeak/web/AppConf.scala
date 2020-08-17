package gospeak.web

import cats.data.NonEmptyList
import com.mohiva.play.silhouette.crypto.{JcaCrypterSettings, JcaSignerSettings}
import com.typesafe.config.Config
import gospeak.core.services.email.EmailConf
import gospeak.core.services.meetup.MeetupConf
import gospeak.core.services.places.GoogleMapsConf
import gospeak.core.services.storage.DbConf
import gospeak.core.services.twitter.TwitterConf
import gospeak.core.services.upload.UploadConf
import gospeak.core.services.video.YoutubeConf
import gospeak.core.{ApplicationConf, GsConf}
import gospeak.libs.scala.Crypto.AesSecretKey
import gospeak.libs.scala.Extensions._
import gospeak.libs.scala.domain.{Creds, EmailAddress, Liquid, LiquidMarkdown, Secret}
import gospeak.web.auth.AuthConf
import gospeak.web.services.SchedulerSrv
import play.api.Configuration
import play.api.mvc.Cookie.SameSite
import pureconfig.error.{CannotConvert, ConfigReaderFailure, ConfigReaderFailures, ConvertFailure}
import pureconfig.module.cron4s.cronExprConfigConvert
import pureconfig.{ConfigCursor, ConfigReader, ConfigSource, Derivation}

import scala.util.{Failure, Success, Try}

final case class AppConf(app: ApplicationConf,
                         auth: AuthConf,
                         database: DbConf,
                         email: EmailConf,
                         upload: UploadConf,
                         meetup: MeetupConf,
                         youtube: YoutubeConf,
                         googleMaps: GoogleMapsConf,
                         twitter: TwitterConf,
                         gospeak: GsConf,
                         scheduler: SchedulerSrv.Conf)

object AppConf {
  def load(conf: Config): Try[AppConf] = {
    ConfigSource.fromConfig(conf).load[AppConf](Readers.reader) match {
      case Right(appConf) => Success(appConf)
      case Left(failures) => Failure(new IllegalArgumentException("Unable to load AppConf:\n" + failures.toList.map(format).mkString("\n")))
    }
  }

  def load(conf: Configuration): Try[AppConf] = load(conf.underlying)

  private def format(f: ConfigReaderFailure): String = "  - " + f.description + f.description

  private object Readers {

    import pureconfig.generic.semiauto._

    val _ = cronExprConfigConvert // to keep import for deriveReader[SchedulerSrv.Conf]

    private def convertErr(cur: ConfigCursor, toType: String, because: String): ConfigReaderFailures =
      ConfigReaderFailures(ConvertFailure(CannotConvert(cur.toString, toType, because), cur.origin, cur.path))

    private implicit val secretReader: ConfigReader[Secret] = deriveReader[Secret]
    private implicit val credsReader: ConfigReader[Creds] = deriveReader[Creds]
    private implicit val credsOptReader: ConfigReader[Option[Creds]] = (cur: ConfigCursor) => {
      if (cur.isUndefined) {
        Right(None)
      } else {
        cur.asObjectCursor.flatMap { obj =>
          if (obj.isEmpty) {
            Right(None)
          } else {
            credsReader.from(obj).map(Some(_))
          }
        }
      }
    }
    private implicit val aesSecretKeyReader: ConfigReader[AesSecretKey] = (cur: ConfigCursor) => cur.asString.map(AesSecretKey)
    private implicit val adminsReader: ConfigReader[NonEmptyList[EmailAddress]] = (cur: ConfigCursor) => cur.asString.flatMap { admins =>
      admins.split(',').toList.map(EmailAddress.from).sequence.flatMap(_.toNel)
        .left.map(e => convertErr(cur, "NonEmptyList[EmailAddress]", e.getMessage))
    }

    private implicit val applicationConfEnvReader: ConfigReader[ApplicationConf.Env] = (cur: ConfigCursor) => cur.asString.flatMap {
      case "local" => Right(ApplicationConf.Env.Local)
      case "dev" => Right(ApplicationConf.Env.Dev)
      case "staging" => Right(ApplicationConf.Env.Staging)
      case "prod" => Right(ApplicationConf.Env.Prod)
      case _ => Left(convertErr(cur, "ApplicationConf.Env", "Invalid value"))
    }

    private implicit val sameSiteReader: ConfigReader[SameSite] = ConfigReader.fromString[SameSite](str => SameSite.parse(str).toEither(CannotConvert(str, "SameSite", s"possible values: '${SameSite.Strict.value}' or '${SameSite.Lax.value}'")))
    private implicit val authConfCookieSettingsReader: ConfigReader[AuthConf.CookieSettings] = deriveReader[AuthConf.CookieSettings]
    private implicit val jcaSignerSettingsReader: ConfigReader[JcaSignerSettings] = deriveReader[JcaSignerSettings]
    private implicit val jcaCrypterSettingsReader: ConfigReader[JcaCrypterSettings] = deriveReader[JcaCrypterSettings]
    private implicit val authConfRememberMeReader: ConfigReader[AuthConf.RememberMe] = deriveReader[AuthConf.RememberMe]
    private implicit val authConfCookieConfReader: ConfigReader[AuthConf.CookieConf] = deriveReader[AuthConf.CookieConf]
    private implicit val facebookAuthConfReader: ConfigReader[AuthConf.FacebookConf] = deriveReader[AuthConf.FacebookConf]
    private implicit val githubAuthConfReader: ConfigReader[AuthConf.GithubConf] = deriveReader[AuthConf.GithubConf]
    private implicit val googleAuthConfReader: ConfigReader[AuthConf.GoogleConf] = deriveReader[AuthConf.GoogleConf]
    private implicit val linkedinAuthConfReader: ConfigReader[AuthConf.LinkedinConf] = deriveReader[AuthConf.LinkedinConf]
    private implicit val twitterAuthConfReader: ConfigReader[AuthConf.TwitterConf] = deriveReader[AuthConf.TwitterConf]

    private implicit val databaseConfReader: ConfigReader[DbConf] = (cur: ConfigCursor) => cur.asString
      .flatMap(DbConf.from(_).leftMap(_ => convertErr(cur, "DatabaseConf", "Invalid value")))


    private implicit val uploadConfUrlReader: ConfigReader[UploadConf.Url] = deriveReader[UploadConf.Url]
    private implicit val uploadConfCloudinaryReader: ConfigReader[UploadConf.Cloudinary] = deriveReader[UploadConf.Cloudinary]

    private implicit def liquidReader[A]: ConfigReader[Liquid[A]] = (cur: ConfigCursor) => cur.asString.map(_.stripPrefix("\n")).map(Liquid[A])

    private implicit def liquidMarkdownReader[A]: ConfigReader[LiquidMarkdown[A]] = (cur: ConfigCursor) => cur.asString.map(_.stripPrefix("\n")).map(LiquidMarkdown[A])

    private implicit val gospeakEventConfReader: ConfigReader[GsConf.EventConf] = deriveReader[GsConf.EventConf]
    private implicit val gospeakProposalConfReader: ConfigReader[GsConf.ProposalConf] = deriveReader[GsConf.ProposalConf]

    private implicit val applicationConfReader: ConfigReader[ApplicationConf] = deriveReader[ApplicationConf]
    private implicit val authConfReader: ConfigReader[AuthConf] = deriveReader[AuthConf]
    private implicit val emailConfConsoleReader: ConfigReader[EmailConf.Console] = deriveReader[EmailConf.Console]
    private implicit val emailConfInMemoryReader: ConfigReader[EmailConf.InMemory] = deriveReader[EmailConf.InMemory]
    private implicit val emailConfSendGridReader: ConfigReader[EmailConf.SendGrid] = deriveReader[EmailConf.SendGrid]
    private implicit val emailConfReader: ConfigReader[EmailConf] = deriveReader[EmailConf]
    private implicit val uploadConfReader: ConfigReader[UploadConf] = deriveReader[UploadConf]
    private implicit val meetupConfDisabledReader: ConfigReader[MeetupConf.Disabled] = deriveReader[MeetupConf.Disabled]
    private implicit val meetupConfEnabledReader: ConfigReader[MeetupConf.Enabled] = deriveReader[MeetupConf.Enabled]
    private implicit val meetupConfReader: ConfigReader[MeetupConf] = deriveReader[MeetupConf]
    private implicit val youtubeConfDisabledReader: ConfigReader[YoutubeConf.Disabled] = deriveReader[YoutubeConf.Disabled]
    private implicit val youtubeConfEnabledReader: ConfigReader[YoutubeConf.Enabled] = deriveReader[YoutubeConf.Enabled]
    private implicit val youtubeConfReader: ConfigReader[YoutubeConf] = deriveReader[YoutubeConf]
    private implicit val googleMapsConfReader: ConfigReader[GoogleMapsConf] = deriveReader[GoogleMapsConf]
    private implicit val twitterConfConsoleReader: ConfigReader[TwitterConf.Console] = deriveReader[TwitterConf.Console]
    private implicit val twitterConfTwitterReader: ConfigReader[TwitterConf.Twitter] = deriveReader[TwitterConf.Twitter]
    private implicit val twitterConfReader: ConfigReader[TwitterConf] = deriveReader[TwitterConf]
    private implicit val gsConfReader: ConfigReader[GsConf] = deriveReader[GsConf]
    private implicit val schedulerConfReader: ConfigReader[SchedulerSrv.Conf] = deriveReader[SchedulerSrv.Conf]

    private implicit val appConfReader: ConfigReader[AppConf] = deriveReader[AppConf]

    val reader: Derivation[ConfigReader[AppConf]] = implicitly[Derivation[ConfigReader[AppConf]]]
  }

}
