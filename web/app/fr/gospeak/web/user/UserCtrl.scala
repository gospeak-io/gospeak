package fr.gospeak.web.user

import fr.gospeak.web.HomeCtrl
import fr.gospeak.web.user.UserCtrl._
import fr.gospeak.web.views.domain._
import play.api.mvc._

class UserCtrl(cc: ControllerComponents) extends AbstractController(cc) {
  def index(): Action[AnyContent] = Action { implicit req: Request[AnyContent] =>
    Ok(views.html.index()(indexHeader, breadcrumb(user)))
  }

  def profile(): Action[AnyContent] = Action { implicit req: Request[AnyContent] =>
    Ok(views.html.profile()(header.activeFor(routes.UserCtrl.profile()), breadcrumb(user).add("Profile" -> routes.UserCtrl.profile())))
  }
}

object UserCtrl {
  val user = "Loïc"

  val userNav: Seq[NavLink] = Seq(
    NavLink("Groups", groups.routes.GroupCtrl.list()),
    NavLink("Talks", talks.routes.TalkCtrl.list()),
    NavLink("Profile", routes.UserCtrl.profile()))

  val indexHeader: HeaderInfo = HeaderInfo(
    brand = NavLink("Gospeak", fr.gospeak.web.routes.HomeCtrl.index()),
    links = NavDropdown("Public", HomeCtrl.publicNav) +: userNav,
    rightLinks = Seq(NavLink("logout", fr.gospeak.web.auth.routes.AuthCtrl.logout())))

  val header: HeaderInfo =
    indexHeader.copy(brand = NavLink("Gospeak", routes.UserCtrl.index()))

  def breadcrumb(user: String) = Breadcrumb(Seq(
    BreadcrumbLink("Public", fr.gospeak.web.routes.HomeCtrl.index()),
    BreadcrumbLink(user, routes.UserCtrl.index())))
}
