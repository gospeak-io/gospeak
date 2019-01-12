package fr.gospeak.web.user.groups.proposals

import fr.gospeak.core.domain.Proposal
import fr.gospeak.web.Values
import fr.gospeak.web.user.groups.GroupCtrl
import fr.gospeak.web.user.groups.proposals.ProposalCtrl._
import fr.gospeak.web.views.domain.{Breadcrumb, HeaderInfo, NavLink}
import play.api.mvc._

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class ProposalCtrl(cc: ControllerComponents) extends AbstractController(cc) {
  private val user = Values.user // logged user

  def list(group: String, search: Option[String], sortBy: Option[String], page: Option[Int], pageSize: Option[Int]): Action[AnyContent] = Action.async { implicit req: Request[AnyContent] =>
    for {
      groupIdOpt <- Values.getGroupId(group)
      groupOpt <- groupIdOpt.map(Values.getGroup(_, user.id)).getOrElse(Future.successful(None))
      proposals <- groupIdOpt.map(Values.getProposals).getOrElse(Future.successful(Seq()))
    } yield groupOpt
      .map { groupElt => Ok(views.html.list(groupElt, proposals)(listHeader(group), listBreadcrumb(user.name, group -> groupElt.name.value))) }
      .getOrElse(NotFound)
  }

  def detail(group: String, proposal: String): Action[AnyContent] = Action.async { implicit req: Request[AnyContent] =>
    val proposalId = Proposal.Id.from(proposal).get
    for {
      groupIdOpt <- Values.getGroupId(group)
      groupOpt <- groupIdOpt.map(Values.getGroup(_, user.id)).getOrElse(Future.successful(None))
      proposalOpt <- Values.getProposal(proposalId)
    } yield {
      (for {
        groupElt <- groupOpt
        proposalElt <- proposalOpt
      } yield Ok(views.html.detail(proposalElt)(header(group), breadcrumb(user.name, group -> groupElt.name.value, proposal -> proposalElt.title.value)))).getOrElse(NotFound)
    }
  }
}

object ProposalCtrl {
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
