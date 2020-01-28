package gospeak.web.pages.published

import cats.effect.IO
import com.mohiva.play.silhouette.api.Silhouette
import gospeak.web.AppConf
import gospeak.web.auth.domain.CookieEnv
import gospeak.web.domain.Breadcrumb
import gospeak.web.pages.published.HomeCtrl._
import gospeak.web.utils.UICtrl
import play.api.mvc._

class HomeCtrl(cc: ControllerComponents,
               silhouette: Silhouette[CookieEnv],
               conf: AppConf) extends UICtrl(cc, silhouette, conf) {
  def index(): Action[AnyContent] = UserAwareAction { implicit req =>
    IO.pure(Ok(html.index()(breadcrumb())))
  }

  def why(): Action[AnyContent] = UserAwareAction { implicit req =>
    IO.pure(Ok(html.why()(breadcrumb().add("Why use Gospeak" -> routes.HomeCtrl.why()))))
  }
}

object HomeCtrl {
  def breadcrumb(): Breadcrumb =
    Breadcrumb("Home" -> routes.HomeCtrl.index())
}
