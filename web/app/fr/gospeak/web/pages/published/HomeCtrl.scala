package fr.gospeak.web.pages.published

import cats.effect.IO
import com.mohiva.play.silhouette.api.Silhouette
import fr.gospeak.web.AppConf
import fr.gospeak.web.auth.domain.CookieEnv
import fr.gospeak.web.domain.Breadcrumb
import fr.gospeak.web.pages.published.HomeCtrl._
import fr.gospeak.web.utils.UICtrl
import play.api.mvc._

class HomeCtrl(cc: ControllerComponents,
               silhouette: Silhouette[CookieEnv],
               conf: AppConf) extends UICtrl(cc, silhouette, conf) with UICtrl.UserAwareAction {
  def index(): Action[AnyContent] = UserAwareAction(implicit req => implicit ctx => {
    IO.pure(Ok(html.index()(breadcrumb())))
  })

  def why(): Action[AnyContent] = UserAwareAction(implicit req => implicit ctx => {
    IO.pure(Ok(html.why()(breadcrumb().add("Why use Gospeak" -> routes.HomeCtrl.why()))))
  })
}

object HomeCtrl {
  def breadcrumb(): Breadcrumb =
    Breadcrumb("Home" -> routes.HomeCtrl.index())
}
