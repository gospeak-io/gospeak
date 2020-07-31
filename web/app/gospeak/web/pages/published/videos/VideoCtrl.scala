package gospeak.web.pages.published.videos

import cats.data.OptionT
import com.mohiva.play.silhouette.api.Silhouette
import gospeak.core.domain.Video
import gospeak.core.services.storage.PublicVideoRepo
import gospeak.infra.services.EmbedSrv
import gospeak.libs.scala.domain.{Html, Page, Url}
import gospeak.web.AppConf
import gospeak.web.auth.domain.CookieEnv
import gospeak.web.domain.Breadcrumb
import gospeak.web.pages.published.HomeCtrl
import gospeak.web.pages.published.videos.VideoCtrl._
import gospeak.web.utils.UICtrl
import play.api.mvc.{Action, AnyContent, ControllerComponents}

class VideoCtrl(cc: ControllerComponents,
                silhouette: Silhouette[CookieEnv],
                conf: AppConf,
                videoRepo: PublicVideoRepo,
                embedSrv: EmbedSrv) extends UICtrl(cc, silhouette, conf) {
  def list(params: Page.Params): Action[AnyContent] = UserAwareAction { implicit req =>
    videoRepo.list(params).map(videos => Ok(html.list(videos)(listBreadcrumb())))
  }

  def detail(video: Url.Video.Id): Action[AnyContent] = UserAwareAction { implicit req =>
    (for {
      videoElt <- OptionT(videoRepo.find(video))
      embed: Html <- OptionT.liftF(embedSrv.embedCode(videoElt.url))
      b = breadcrumb(videoElt)
    } yield Ok(html.detail(videoElt, embed)(b))).value.map(_.getOrElse(publicVideoNotFound(video)))
  }
}

object VideoCtrl {
  def listBreadcrumb(): Breadcrumb =
    HomeCtrl.breadcrumb().add("Videos" -> routes.VideoCtrl.list())

  def breadcrumb(video: Video): Breadcrumb =
    listBreadcrumb().add(video.title -> routes.VideoCtrl.detail(video.id))
}
