package fr.gospeak.web

import fr.gospeak.web.HomeCtrl._
import fr.gospeak.web.domain.{HeaderInfo, NavLink}
import play.api.mvc._

class HomeCtrl(cc: ControllerComponents) extends AbstractController(cc) {
  def index(): Action[AnyContent] = Action { implicit req: Request[AnyContent] =>
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
