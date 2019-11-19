package fr.gospeak.web.pages.published

import cats.effect.IO
import com.mohiva.play.silhouette.api.Silhouette
import fr.gospeak.core.ApplicationConf
import fr.gospeak.web.auth.domain.CookieEnv
import fr.gospeak.web.domain.Breadcrumb
import fr.gospeak.web.pages.published.HomeCtrl._
import fr.gospeak.web.utils.UICtrl
import play.api.mvc._

class HomeCtrl(cc: ControllerComponents,
               silhouette: Silhouette[CookieEnv],
               env: ApplicationConf.Env) extends UICtrl(cc, silhouette, env) {
  def index(): Action[AnyContent] = UserAwareActionIO { implicit req =>
    val b = breadcrumb()
    IO.pure(Ok(html.index()(b)))
  }

  def why(): Action[AnyContent] = UserAwareActionIO { implicit req =>
    val b = breadcrumb().add("Why use Gospeak" -> routes.HomeCtrl.why())
    IO.pure(Ok(html.why()(b)))
  }
}

object HomeCtrl {
  def breadcrumb(): Breadcrumb =
    Breadcrumb("Home" -> routes.HomeCtrl.index())
}
