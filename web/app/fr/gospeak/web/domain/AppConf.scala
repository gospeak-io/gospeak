package fr.gospeak.web.domain

import com.typesafe.config.{Config, ConfigFactory}
import fr.gospeak.infra.services.EmailSrv
import fr.gospeak.infra.services.storage.sql.DatabaseConf
import fr.gospeak.libs.scalautils.Extensions._
import fr.gospeak.web.auth.AuthConf
import play.api.Configuration
import play.api.mvc.Cookie.SameSite
import pureconfig.ConfigReader
import pureconfig.error.{CannotConvert, ConfigReaderFailure}
import pureconfig.generic.EnumCoproductHint

import scala.util.{Failure, Success, Try}

final case class AppConf(application: ApplicationConf,
                         auth: AuthConf,
                         database: DatabaseConf,
                         emailService: EmailSrv.Conf)

object AppConf {
  def load(conf: Config): Try[AppConf] = {
    import pureconfig.generic.auto._
    val _ = exportReader[ApplicationConf] // to help scala compiler & prevent IntelliJ from removing import
    implicit val sameSiteReader: ConfigReader[SameSite] = ConfigReader.fromString[SameSite](str => SameSite.parse(str).toEither(CannotConvert(str, "SameSite", s"possible values: '${SameSite.Strict.value}' or '${SameSite.Lax.value}'")))
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

final case class ApplicationConf(env: ApplicationConf.Env)

object ApplicationConf {

  sealed trait Env

  object Env {
    implicit val hint: EnumCoproductHint[Env] = new EnumCoproductHint[Env]
  }

  final case object Local extends Env

  final case object Dev extends Env

  final case object Prod extends Env

}
