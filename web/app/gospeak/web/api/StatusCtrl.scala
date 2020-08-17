package gospeak.web.api

import java.time.Instant

import cats.effect.IO
import com.mohiva.play.silhouette.api.Silhouette
import generated.BuildInfo
import gospeak.core.services.cloudinary.UploadSrv
import gospeak.core.services.email.EmailSrv
import gospeak.core.services.meetup.MeetupSrv
import gospeak.core.services.slack.SlackSrv
import gospeak.core.services.twitter.TwitterSrv
import gospeak.core.services.video.VideoSrv
import gospeak.web.AppConf
import gospeak.web.api.StatusCtrl._
import gospeak.web.api.domain.utils.ApiResult
import gospeak.web.auth.domain.CookieEnv
import gospeak.web.utils.ApiCtrl
import play.api.libs.json._
import play.api.mvc.{Action, AnyContent, ControllerComponents}

import scala.util.Try

class StatusCtrl(cc: ControllerComponents,
                 silhouette: Silhouette[CookieEnv],
                 conf: AppConf,
                 uploadSrv: UploadSrv,
                 emailSrv: EmailSrv,
                 meetupSrvEth: Either[String, MeetupSrv],
                 slackSrv: SlackSrv,
                 twitterSrv: TwitterSrv,
                 videoSrv: VideoSrv) extends ApiCtrl(cc, silhouette, conf) {
  private val startedAt = Instant.now()

  def getStatus: Action[AnyContent] = UserAwareAction { implicit req =>
    IO.pure(ApiResult.of(AppStatus(startedAt, generated.BuildInfo, conf, uploadSrv, emailSrv, twitterSrv, meetupSrvEth, slackSrv, videoSrv)))
  }

}

object StatusCtrl {
  implicit val gitStatusWrites: Writes[GitStatus] = Json.writes[GitStatus]
  implicit val srvStatusWrites: Writes[SrvStatus] = Json.writes[SrvStatus]
  implicit val appStatusWrites: Writes[AppStatus] = Json.writes[AppStatus]

  final case class SrvStatus(db: String,
                             upload: String,
                             email: String,
                             twitter: String,
                             meetup: String,
                             slack: String,
                             youtube: String,
                             vimeo: String,
                             infoq: String)

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
    def apply(startedAt: Instant,
              info: BuildInfo.type,
              conf: AppConf,
              uploadSrv: UploadSrv,
              emailSrv: EmailSrv,
              twitterSrv: TwitterSrv,
              meetupSrvEth: Either[String, MeetupSrv],
              slackSrv: SlackSrv,
              videoSrv: VideoSrv): AppStatus =
      new AppStatus(
        env = conf.app.env.value,
        buildAt = Instant.ofEpochMilli(info.builtAtMillis),
        startedAt = startedAt,
        appVersion = info.version,
        scalaVersion = info.scalaVersion,
        sbtVersion = info.sbtVersion,
        services = SrvStatus(
          db = conf.database.getClass.getSimpleName,
          upload = uploadSrv.getClass.getSimpleName,
          email = emailSrv.getClass.getSimpleName,
          twitter = twitterSrv.getClass.getSimpleName,
          meetup = meetupSrvEth.map(srv => if (srv.performWriteOps) "read/write" else "read only").getOrElse("disabled"),
          slack = "read/write", // only mode with slackSrv for now
          youtube = if (videoSrv.youtube) "available" else "not available",
          vimeo = if (videoSrv.vimeo) "available" else "not available",
          infoq = if (videoSrv.infoq) "available" else "not available"),
        lastCommit = GitStatus(
          subject = info.gitSubject,
          branch = info.gitBranch,
          hash = info.gitHash,
          commiter = info.gitCommitterName,
          date = Try(Instant.ofEpochSecond(info.gitCommitterDate.toLong)).toOption))
  }

}
