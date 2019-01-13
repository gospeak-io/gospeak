package fr.gospeak.web.user.groups

import fr.gospeak.core.domain.{Group, User}
import fr.gospeak.web.user.UserCtrl
import fr.gospeak.web.user.groups.GroupCtrl._
import fr.gospeak.web.views.domain._
import fr.gospeak.web.{HomeCtrl, Values}
import play.api.mvc._

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class GroupCtrl(cc: ControllerComponents) extends AbstractController(cc) {
  private val user = Values.user // logged user

  def list(): Action[AnyContent] = Action.async { implicit req: Request[AnyContent] =>
    for {
      groups <- Values.getGroups(user.id)
    } yield Ok(views.html.list(groups)(UserCtrl.header.activeFor(routes.GroupCtrl.list()), listBreadcrumb(user.name)))
  }

  def detail(group: Group.Slug): Action[AnyContent] = Action.async { implicit req: Request[AnyContent] =>
    for {
      groupIdOpt <- Values.getGroupId(group)
      groupOpt <- groupIdOpt.map(Values.getGroup(_, user.id)).getOrElse(Future.successful(None))
      events <- groupOpt.map(g => Values.getEvents(g.id)).getOrElse(Future.successful(Seq()))
    } yield groupOpt
      .map { groupElt => Ok(views.html.detail(groupElt, events)(header(group), breadcrumb(user.name, group -> groupElt.name))) }
      .getOrElse(NotFound)
  }
}

object GroupCtrl {
  def groupNav(group: Group.Slug): Seq[NavLink] = Seq(
    NavLink("Events", events.routes.EventCtrl.list(group)),
    NavLink("Proposals", proposals.routes.ProposalCtrl.list(group)))

  def listBreadcrumb(user: User.Name): Breadcrumb =
    UserCtrl.breadcrumb(user).add("Groups" -> routes.GroupCtrl.list())

  def header(group: Group.Slug): HeaderInfo = HeaderInfo(
    brand = NavLink("Gospeak", fr.gospeak.web.user.routes.UserCtrl.index()),
    links = NavDropdown("Public", HomeCtrl.publicNav) +: NavDropdown("User", UserCtrl.userNav) +: groupNav(group),
    rightLinks = Seq(NavLink("logout", fr.gospeak.web.auth.routes.AuthCtrl.logout())))

  def breadcrumb(user: User.Name, group: (Group.Slug, Group.Name)): Breadcrumb =
    listBreadcrumb(user).add(group._2.value -> routes.GroupCtrl.detail(group._1))
}
