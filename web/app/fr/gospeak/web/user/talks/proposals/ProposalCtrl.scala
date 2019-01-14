package fr.gospeak.web.user.talks.proposals

import cats.data.OptionT
import cats.instances.future._
import fr.gospeak.core.domain.{Group, Proposal, Talk, User}
import fr.gospeak.core.services.GospeakDb
import fr.gospeak.web.user.talks.TalkCtrl
import fr.gospeak.web.user.talks.proposals.ProposalCtrl._
import fr.gospeak.web.domain.Breadcrumb
import play.api.mvc._

import scala.concurrent.ExecutionContext.Implicits.global

class ProposalCtrl(cc: ControllerComponents, db: GospeakDb) extends AbstractController(cc) {
  private val user = db.getUser() // logged user

  def list(talk: Talk.Slug): Action[AnyContent] = Action.async { implicit req: Request[AnyContent] =>
    (for {
      talkId <- OptionT(db.getTalkId(talk))
      talkElt <- OptionT(db.getTalk(talkId, user.id))
      proposals <- OptionT.liftF(db.getProposals(talkId))
      h = TalkCtrl.header(talkElt.slug)
      b = listBreadcrumb(user.name, talk -> talkElt.title)
    } yield Ok(html.list(talkElt, proposals)(h, b))).value.map(_.getOrElse(NotFound))
  }

  def detail(talk: Talk.Slug, proposal: Proposal.Id): Action[AnyContent] = Action.async { implicit req: Request[AnyContent] =>
    (for {
      talkId <- OptionT(db.getTalkId(talk))
      talkElt <- OptionT(db.getTalk(talkId, user.id))
      proposalElt <- OptionT(db.getProposal(proposal))
      groupElt <- OptionT(db.getGroup(proposalElt.group, user.id))
      h = TalkCtrl.header(talkElt.slug)
      b = breadcrumb(user.name, talk -> talkElt.title, proposal -> groupElt.name)
    } yield Ok(html.detail(groupElt, proposalElt)(h, b))).value.map(_.getOrElse(NotFound))
  }
}

object ProposalCtrl {
  def listBreadcrumb(user: User.Name, talk: (Talk.Slug, Talk.Title)): Breadcrumb =
    TalkCtrl.breadcrumb(user, talk).add("Proposals" -> routes.ProposalCtrl.list(talk._1))

  def breadcrumb(user: User.Name, talk: (Talk.Slug, Talk.Title), proposal: (Proposal.Id, Group.Name)): Breadcrumb =
    listBreadcrumb(user, talk).add(proposal._2.value -> routes.ProposalCtrl.detail(talk._1, proposal._1))
}
