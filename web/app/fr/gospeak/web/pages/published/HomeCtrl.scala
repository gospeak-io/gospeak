package fr.gospeak.web.pages.published

import com.mohiva.play.silhouette.api.Silhouette
import fr.gospeak.web.auth.domain.CookieEnv
import fr.gospeak.web.utils.UICtrl
import play.api.mvc._

class HomeCtrl(cc: ControllerComponents,
               silhouette: Silhouette[CookieEnv]) extends UICtrl(cc, silhouette) {

  import silhouette._

  def index(): Action[AnyContent] = UserAwareAction { implicit req =>
    Ok(html.index())
  }
}
