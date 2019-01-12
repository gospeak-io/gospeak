package fr.gospeak.web.user.groups.proposals

import fr.gospeak.web.user.UserCtrl
import fr.gospeak.web.user.groups.GroupCtrl
import fr.gospeak.web.user.groups.proposals.ProposalCtrl._
import fr.gospeak.web.views.domain.{Breadcrumb, HeaderInfo, NavLink}
import play.api.mvc._

class ProposalCtrl(cc: ControllerComponents) extends AbstractController(cc) {
  def list(group: String, search: Option[String], sortBy: Option[String], page: Option[Int], pageSize: Option[Int]): Action[AnyContent] = Action { implicit req: Request[AnyContent] =>
    Ok(views.html.list()(listHeader(group), listBreadcrumb(UserCtrl.user, group -> GroupCtrl.groupName)))
  }

  def detail(group: String, proposal: String): Action[AnyContent] = Action { implicit req: Request[AnyContent] =>
    Ok(views.html.detail()(header(group), breadcrumb(UserCtrl.user, group -> GroupCtrl.groupName, proposal -> proposalName)))
  }
}

object ProposalCtrl {
  val proposalName = "Why FP"

  def listHeader(group: String): HeaderInfo =
    GroupCtrl.header(group)
      .copy(brand = NavLink("Gospeak", fr.gospeak.web.user.groups.routes.GroupCtrl.detail(group)))
      .activeFor(routes.ProposalCtrl.list(group))

  def listBreadcrumb(user: String, group: (String, String)): Breadcrumb =
    GroupCtrl.breadcrumb(user, group).add("Proposals" -> routes.ProposalCtrl.list(group._1))

  def header(group: String): HeaderInfo =
    listHeader(group)

  def breadcrumb(user: String, group: (String, String), event: (String, String)): Breadcrumb =
    listBreadcrumb(user, group).add(event._2 -> routes.ProposalCtrl.detail(group._1, event._1))
}
