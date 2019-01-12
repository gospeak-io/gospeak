package fr.gospeak.web.user.groups

import fr.gospeak.web.HomeCtrl
import fr.gospeak.web.user.UserCtrl
import fr.gospeak.web.user.groups.GroupCtrl._
import fr.gospeak.web.views.domain._
import play.api.mvc._

class GroupCtrl(cc: ControllerComponents) extends AbstractController(cc) {
  def list(): Action[AnyContent] = Action { implicit req: Request[AnyContent] =>
    Ok(views.html.list()(UserCtrl.header.activeFor(routes.GroupCtrl.list()), listBreadcrumb(UserCtrl.user)))
  }

  def detail(group: String): Action[AnyContent] = Action { implicit req: Request[AnyContent] =>
    Ok(views.html.detail()(header(group), breadcrumb(UserCtrl.user, group -> groupName)))
  }
}

object GroupCtrl {
  val groupName = "HumanTalks Paris"

  def groupNav(group: String): Seq[NavLink] = Seq(
    NavLink("Events", events.routes.EventCtrl.list(group)),
    NavLink("Proposals", proposals.routes.ProposalCtrl.list(group)))

  def listBreadcrumb(user: String): Breadcrumb =
    UserCtrl.breadcrumb(user).add("Groups" -> routes.GroupCtrl.list())

  def header(group: String): HeaderInfo = HeaderInfo(
    brand = NavLink("Gospeak", fr.gospeak.web.user.routes.UserCtrl.index()),
    links = NavDropdown("Public", HomeCtrl.publicNav) +: NavDropdown("User", UserCtrl.userNav) +: groupNav(group),
    rightLinks = Seq(NavLink("logout", fr.gospeak.web.auth.routes.AuthCtrl.logout())))

  def breadcrumb(user: String, group: (String, String)): Breadcrumb =
    listBreadcrumb(user).add(group._2 -> routes.GroupCtrl.detail(group._1))
}
