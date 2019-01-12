package fr.gospeak.web.user.talks.proposals

import fr.gospeak.core.domain.Proposal
import fr.gospeak.web.Values
import fr.gospeak.web.user.talks.TalkCtrl
import fr.gospeak.web.user.talks.proposals.ProposalCtrl._
import fr.gospeak.web.views.domain.Breadcrumb
import play.api.mvc._

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class ProposalCtrl(cc: ControllerComponents) extends AbstractController(cc) {
  private val user = Values.user // logged user

  def list(talk: String): Action[AnyContent] = Action.async { implicit req: Request[AnyContent] =>
    for {
      talkId <- Values.getTalkId(talk)
      talkOpt <- talkId.map(Values.getTalk(_, user.id)).getOrElse(Future.successful(None))
      proposals <- talkId.map(Values.getProposals).getOrElse(Future.successful(Seq()))
    } yield talkOpt
      .map { talkElt => Ok(views.html.list(talkElt, proposals)(TalkCtrl.header(talkElt.slug.value), listBreadcrumb(user.name, talk -> talkElt.title.value))) }
      .getOrElse(NotFound)
  }

  def detail(talk: String, proposal: String): Action[AnyContent] = Action.async { implicit req: Request[AnyContent] =>
    val proposalId = Proposal.Id.from(proposal).get
    for {
      talkIdOpt <- Values.getTalkId(talk)
      talkOpt <- talkIdOpt.map(Values.getTalk(_, user.id)).getOrElse(Future.successful(None))
      proposalOpt <- Values.getProposal(proposalId)
      groupOpt <- proposalOpt.map(p => Values.getGroup(p.group, user.id)).getOrElse(Future.successful(None))
    } yield {
      (for {
        proposalElt <- proposalOpt
        talkElt <- talkOpt
        groupElt <- groupOpt
      } yield {
        Ok(views.html.detail(groupElt, proposalElt)(TalkCtrl.header(talkElt.slug.value), breadcrumb(user.name, talk -> talkElt.title.value, proposal -> groupElt.name.value)))
      }).getOrElse(NotFound)
    }
  }
}

object ProposalCtrl {
  def listBreadcrumb(user: String, talk: (String, String)): Breadcrumb =
    TalkCtrl.breadcrumb(user, talk).add("Proposals" -> routes.ProposalCtrl.list(talk._1))

  def breadcrumb(user: String, talk: (String, String), proposal: (String, String)): Breadcrumb =
    listBreadcrumb(user, talk).add(proposal._2 -> routes.ProposalCtrl.detail(talk._1, proposal._1))
}
