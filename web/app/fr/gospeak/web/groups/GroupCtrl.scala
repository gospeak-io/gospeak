package fr.gospeak.web.groups

import com.mohiva.play.silhouette.api.Silhouette
import fr.gospeak.web.HomeCtrl
import fr.gospeak.web.auth.domain.CookieEnv
import fr.gospeak.web.domain.HeaderInfo
import fr.gospeak.web.groups.GroupCtrl._
import fr.gospeak.web.utils.UICtrl
import play.api.mvc._

class GroupCtrl(cc: ControllerComponents,
                silhouette: Silhouette[CookieEnv]) extends UICtrl(cc, silhouette) {

  import silhouette._

  def list(): Action[AnyContent] = UserAwareAction { implicit req =>
    Ok(html.list()(header))
  }
}

object GroupCtrl {
  val header: HeaderInfo =
    HomeCtrl.header.activeFor(routes.GroupCtrl.list())
}
