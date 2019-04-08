package fr.gospeak.web.pages.published.cfps

import com.mohiva.play.silhouette.api.Silhouette
import fr.gospeak.web.auth.domain.CookieEnv
import fr.gospeak.web.domain.HeaderInfo
import fr.gospeak.web.pages.published.HomeCtrl
import fr.gospeak.web.pages.published.cfps.CfpCtrl._
import fr.gospeak.web.utils.UICtrl
import play.api.mvc._

class CfpCtrl(cc: ControllerComponents,
              silhouette: Silhouette[CookieEnv]) extends UICtrl(cc, silhouette) {

  import silhouette._

  def list(): Action[AnyContent] = UserAwareAction { implicit req =>
    Ok(html.list()(header))
  }
}

object CfpCtrl {
  val header: HeaderInfo =
    HomeCtrl.header.activeFor(routes.CfpCtrl.list())
}
