package fr.gospeak.web.user.groups

import cats.data.OptionT
import cats.instances.future._
import fr.gospeak.core.domain.utils.Page
import fr.gospeak.core.domain.{Group, User}
import fr.gospeak.core.services.GospeakDb
import fr.gospeak.web.HomeCtrl
import fr.gospeak.web.user.UserCtrl
import fr.gospeak.web.user.groups.GroupCtrl._
import fr.gospeak.web.domain._
import play.api.mvc._

import scala.concurrent.ExecutionContext.Implicits.global

class GroupCtrl(cc: ControllerComponents, db: GospeakDb) extends AbstractController(cc) {
  private val user = db.getUser() // logged user

  def list(params: Page.Params): Action[AnyContent] = Action.async { implicit req: Request[AnyContent] =>
    for {
      groups <- db.getGroups(user.id, params)
      h = UserCtrl.header.activeFor(routes.GroupCtrl.list())
      b = listBreadcrumb(user.name)
    } yield Ok(html.list(groups)(h, b))
  }

  def detail(group: Group.Slug): Action[AnyContent] = Action.async { implicit req: Request[AnyContent] =>
    (for {
      groupId <- OptionT(db.getGroupId(group))
      groupElt <- OptionT(db.getGroup(groupId, user.id))
      events <- OptionT.liftF(db.getEvents(groupId, Page.Params.defaults))
      h = header(group)
      b = breadcrumb(user.name, group -> groupElt.name)
    } yield Ok(html.detail(groupElt, events)(h, b))).value.map(_.getOrElse(NotFound))
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
