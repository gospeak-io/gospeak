package fr.gospeak.web

import com.mohiva.play.silhouette.api.Silhouette
import fr.gospeak.web.HomeCtrl._
import fr.gospeak.web.auth.domain.CookieEnv
import fr.gospeak.web.domain.{HeaderInfo, NavLink}
import fr.gospeak.web.utils.UICtrl
import play.api.mvc._

class HomeCtrl(cc: ControllerComponents,
               silhouette: Silhouette[CookieEnv]) extends UICtrl(cc, silhouette) {

  import silhouette._

  def index(): Action[AnyContent] = UserAwareAction { implicit req =>
    Ok(html.index()(header))
  }
}

object HomeCtrl {
  val publicNav: Seq[NavLink] = Seq(
    NavLink("Cfps", cfps.routes.CfpCtrl.list()),
    NavLink("Groups", groups.routes.GroupCtrl.list()),
    NavLink("Speakers", speakers.routes.SpeakerCtrl.list()))

  val header: HeaderInfo = HeaderInfo(
    brand = NavLink("Gospeak", routes.HomeCtrl.index()),
    links = publicNav,
    rightLinks = Seq(NavLink("login", auth.routes.AuthCtrl.login())))
}
