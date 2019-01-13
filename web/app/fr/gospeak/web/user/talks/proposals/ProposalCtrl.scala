package fr.gospeak.web.user.talks.proposals

import fr.gospeak.core.domain.{Group, Proposal, Talk, User}
import fr.gospeak.web.Values
import fr.gospeak.web.user.talks.TalkCtrl
import fr.gospeak.web.user.talks.proposals.ProposalCtrl._
import fr.gospeak.web.views.domain.Breadcrumb
import play.api.mvc._

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class ProposalCtrl(cc: ControllerComponents) extends AbstractController(cc) {
  private val user = Values.user // logged user

  def list(talk: Talk.Slug): Action[AnyContent] = Action.async { implicit req: Request[AnyContent] =>
    for {
      talkId <- Values.getTalkId(talk)
      talkOpt <- talkId.map(Values.getTalk(_, user.id)).getOrElse(Future.successful(None))
      proposals <- talkId.map(Values.getProposals).getOrElse(Future.successful(Seq()))
    } yield talkOpt
      .map { talkElt => Ok(views.html.list(talkElt, proposals)(TalkCtrl.header(talkElt.slug), listBreadcrumb(user.name, talk -> talkElt.title))) }
      .getOrElse(NotFound)
  }

  def detail(talk: Talk.Slug, proposal: Proposal.Id): Action[AnyContent] = Action.async { implicit req: Request[AnyContent] =>
    for {
      talkIdOpt <- Values.getTalkId(talk)
      talkOpt <- talkIdOpt.map(Values.getTalk(_, user.id)).getOrElse(Future.successful(None))
      proposalOpt <- Values.getProposal(proposal)
      groupOpt <- proposalOpt.map(p => Values.getGroup(p.group, user.id)).getOrElse(Future.successful(None))
    } yield {
      (for {
        proposalElt <- proposalOpt
        talkElt <- talkOpt
        groupElt <- groupOpt
      } yield {
        Ok(views.html.detail(groupElt, proposalElt)(TalkCtrl.header(talkElt.slug), breadcrumb(user.name, talk -> talkElt.title, proposal -> groupElt.name)))
      }).getOrElse(NotFound)
    }
  }
}

object ProposalCtrl {
  def listBreadcrumb(user: User.Name, talk: (Talk.Slug, Talk.Title)): Breadcrumb =
    TalkCtrl.breadcrumb(user, talk).add("Proposals" -> routes.ProposalCtrl.list(talk._1))

  def breadcrumb(user: User.Name, talk: (Talk.Slug, Talk.Title), proposal: (Proposal.Id, Group.Name)): Breadcrumb =
    listBreadcrumb(user, talk).add(proposal._2.value -> routes.ProposalCtrl.detail(talk._1, proposal._1))
}
