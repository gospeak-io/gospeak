package fr.gospeak.web.api

import java.time.Instant

import cats.effect.IO
import com.mohiva.play.silhouette.api.Silhouette
import fr.gospeak.web.AppConf
import fr.gospeak.web.api.StatusCtrl._
import fr.gospeak.web.api.domain.utils.ApiResponse
import fr.gospeak.web.auth.domain.CookieEnv
import fr.gospeak.web.utils.ApiCtrl
import generated.BuildInfo
import play.api.libs.json._
import play.api.mvc.{Action, AnyContent, ControllerComponents}

import scala.util.Try

class StatusCtrl(cc: ControllerComponents,
                 silhouette: Silhouette[CookieEnv],
                 conf: AppConf) extends ApiCtrl(cc, silhouette, conf) {
  private val startedAt = Instant.now()

  def getStatus: Action[AnyContent] = UserAwareAction { implicit req =>
    IO.pure(ApiResponse.from(AppStatus(startedAt, generated.BuildInfo, conf)))
  }

}

object StatusCtrl {
  implicit val gitStatusWrites: Writes[GitStatus] = Json.writes[GitStatus]
  implicit val srvStatusWrites: Writes[SrvStatus] = Json.writes[SrvStatus]
  implicit val appStatusWrites: Writes[AppStatus] = Json.writes[AppStatus]

  final case class SrvStatus(db: String,
                             email: String,
                             upload: String)

  final case class GitStatus(subject: String,
                             branch: String,
                             hash: String,
                             commiter: String,
                             date: Option[Instant])

  final case class AppStatus(env: String,
                             buildAt: Instant,
                             startedAt: Instant,
                             appVersion: String,
                             scalaVersion: String,
                             sbtVersion: String,
                             services: SrvStatus,
                             lastCommit: GitStatus)

  object AppStatus {
    def apply(startedAt: Instant, info: BuildInfo.type, conf: AppConf): AppStatus =
      new AppStatus(
        env = conf.application.env.toString,
        buildAt = Instant.ofEpochMilli(info.builtAtMillis),
        startedAt = startedAt,
        appVersion = info.version,
        scalaVersion = info.scalaVersion,
        sbtVersion = info.sbtVersion,
        services = SrvStatus(
          db = conf.database.getClass.getSimpleName,
          email = conf.email.getClass.getName.split("\\$").filter(_.nonEmpty).last,
          upload = conf.upload.getClass.getSimpleName),
        lastCommit = GitStatus(
          subject = info.gitSubject,
          branch = info.gitBranch,
          hash = info.gitHash,
          commiter = info.gitCommitterName,
          date = Try(Instant.ofEpochSecond(info.gitCommitterDate.toLong)).toOption))
  }

}
