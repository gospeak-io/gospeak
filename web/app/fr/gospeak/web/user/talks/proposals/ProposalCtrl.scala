package fr.gospeak.web.user.talks.proposals

import cats.data.OptionT
import fr.gospeak.core.domain._
import fr.gospeak.core.domain.utils.Page
import fr.gospeak.core.services.GospeakDb
import fr.gospeak.web.domain.Breadcrumb
import fr.gospeak.web.user.talks.TalkCtrl
import fr.gospeak.web.user.talks.proposals.ProposalCtrl._
import fr.gospeak.web.utils.UICtrl
import play.api.mvc._

class ProposalCtrl(cc: ControllerComponents, db: GospeakDb) extends UICtrl(cc) {
  def list(talk: Talk.Slug, params: Page.Params): Action[AnyContent] = Action.async { implicit req: Request[AnyContent] =>
    implicit val user: User = db.authed() // logged user
    (for {
      talkElt <- OptionT(db.getTalk(user.id, talk))
      proposals <- OptionT.liftF(db.getProposals(talkElt.id, params))
      h = TalkCtrl.header(talkElt.slug)
      b = listBreadcrumb(user.name, talk -> talkElt.title)
    } yield Ok(html.list(talkElt, proposals)(h, b))).value.map(_.getOrElse(talkNotFound(talk))).unsafeToFuture()
  }

  def detail(talk: Talk.Slug, proposal: Proposal.Id): Action[AnyContent] = Action.async { implicit req: Request[AnyContent] =>
    implicit val user: User = db.authed() // logged user
    (for {
      talkElt <- OptionT(db.getTalk(user.id, talk))
      proposalElt <- OptionT(db.getProposal(proposal))
      cfpElt <- OptionT(db.getCfp(proposalElt.cfp))
      h = TalkCtrl.header(talkElt.slug)
      b = breadcrumb(user.name, talk -> talkElt.title, proposal -> cfpElt.name)
    } yield Ok(html.detail(cfpElt, proposalElt)(h, b))).value.map(_.getOrElse(proposalNotFound(talk, proposal))).unsafeToFuture()
  }
}

object ProposalCtrl {
  def listBreadcrumb(user: User.Name, talk: (Talk.Slug, Talk.Title)): Breadcrumb =
    talk match {
      case (talkSlug, _) => TalkCtrl.breadcrumb(user, talk).add("Proposals" -> routes.ProposalCtrl.list(talkSlug))
    }

  def breadcrumb(user: User.Name, talk: (Talk.Slug, Talk.Title), proposal: (Proposal.Id, Cfp.Name)): Breadcrumb =
    (talk, proposal) match {
      case ((talkSlug, _), (proposalId, cfpName)) =>
        listBreadcrumb(user, talk).add(cfpName.value -> routes.ProposalCtrl.detail(talkSlug, proposalId))
    }
}
