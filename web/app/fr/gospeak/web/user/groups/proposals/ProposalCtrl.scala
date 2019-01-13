package fr.gospeak.web.user.groups.proposals

import fr.gospeak.core.domain.{Group, Proposal, User}
import fr.gospeak.web.Values
import fr.gospeak.web.domain.Page
import fr.gospeak.web.user.groups.GroupCtrl
import fr.gospeak.web.user.groups.proposals.ProposalCtrl._
import fr.gospeak.web.views.domain.{Breadcrumb, HeaderInfo, NavLink}
import play.api.mvc._

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class ProposalCtrl(cc: ControllerComponents) extends AbstractController(cc) {
  private val user = Values.user // logged user

  def list(group: Group.Slug, params: Page.Params): Action[AnyContent] = Action.async { implicit req: Request[AnyContent] =>
    for {
      groupIdOpt <- Values.getGroupId(group)
      groupOpt <- groupIdOpt.map(Values.getGroup(_, user.id)).getOrElse(Future.successful(None))
      proposals <- groupIdOpt.map(Values.getProposals).getOrElse(Future.successful(Seq()))
      proposalPage = Page(proposals, params, Page.Total(45))
    } yield groupOpt
      .map { groupElt => Ok(views.html.list(groupElt, proposalPage)(listHeader(group), listBreadcrumb(user.name, group -> groupElt.name))) }
      .getOrElse(NotFound)
  }

  def detail(group: Group.Slug, proposal: Proposal.Id): Action[AnyContent] = Action.async { implicit req: Request[AnyContent] =>
    for {
      groupIdOpt <- Values.getGroupId(group)
      groupOpt <- groupIdOpt.map(Values.getGroup(_, user.id)).getOrElse(Future.successful(None))
      proposalOpt <- Values.getProposal(proposal)
    } yield {
      (for {
        groupElt <- groupOpt
        proposalElt <- proposalOpt
      } yield Ok(views.html.detail(proposalElt)(header(group), breadcrumb(user.name, group -> groupElt.name, proposal -> proposalElt.title)))).getOrElse(NotFound)
    }
  }
}

object ProposalCtrl {
  def listHeader(group: Group.Slug): HeaderInfo =
    GroupCtrl.header(group)
      .copy(brand = NavLink("Gospeak", fr.gospeak.web.user.groups.routes.GroupCtrl.detail(group)))
      .activeFor(routes.ProposalCtrl.list(group))

  def listBreadcrumb(user: User.Name, group: (Group.Slug, Group.Name)): Breadcrumb =
    GroupCtrl.breadcrumb(user, group).add("Proposals" -> routes.ProposalCtrl.list(group._1))

  def header(group: Group.Slug): HeaderInfo =
    listHeader(group)

  def breadcrumb(user: User.Name, group: (Group.Slug, Group.Name), proposal: (Proposal.Id, Proposal.Title)): Breadcrumb =
    listBreadcrumb(user, group).add(proposal._2.value -> routes.ProposalCtrl.detail(group._1, proposal._1))
}
