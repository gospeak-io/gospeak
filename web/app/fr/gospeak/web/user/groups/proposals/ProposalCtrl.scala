package fr.gospeak.web.user.groups.proposals

import cats.data.OptionT
import cats.effect.IO
import fr.gospeak.core.domain._
import fr.gospeak.core.domain.utils.Page
import fr.gospeak.core.services.GospeakDb
import fr.gospeak.web.domain.{Breadcrumb, HeaderInfo, NavLink}
import fr.gospeak.web.user.groups.GroupCtrl
import fr.gospeak.web.user.groups.proposals.ProposalCtrl._
import fr.gospeak.web.utils.UICtrl
import play.api.mvc._

class ProposalCtrl(cc: ControllerComponents, db: GospeakDb) extends UICtrl(cc) {
  def list(group: Group.Slug, params: Page.Params): Action[AnyContent] = Action.async { implicit req: Request[AnyContent] =>
    implicit val user: User = db.authed() // logged user
    (for {
      groupElt <- OptionT(db.getGroup(user.id, group))
      cfpOpt <- OptionT.liftF(db.getCfp(groupElt.id))
      proposals <- cfpOpt.map(cfpElt => OptionT.liftF(db.getProposals(cfpElt.id, params))).getOrElse(OptionT.pure[IO](Page.empty[Proposal](params)))
      h = listHeader(group)
      b = listBreadcrumb(user.name, group -> groupElt.name)
    } yield Ok(html.list(groupElt, cfpOpt, proposals)(h, b))).value.map(_.getOrElse(groupNotFound(group))).unsafeToFuture()
  }

  def detail(group: Group.Slug, proposal: Proposal.Id): Action[AnyContent] = Action.async { implicit req: Request[AnyContent] =>
    implicit val user: User = db.authed() // logged user
    (for {
      groupElt <- OptionT(db.getGroup(user.id, group))
      proposalElt <- OptionT(db.getProposal(proposal))
      h = header(group)
      b = breadcrumb(user.name, group -> groupElt.name, proposal -> proposalElt.title)
    } yield Ok(html.detail(proposalElt)(h, b))).value.map(_.getOrElse(proposalNotFound(group, proposal))).unsafeToFuture()
  }
}

object ProposalCtrl {
  def listHeader(group: Group.Slug): HeaderInfo =
    GroupCtrl.header(group)
      .copy(brand = NavLink("Gospeak", fr.gospeak.web.user.groups.routes.GroupCtrl.detail(group)))
      .activeFor(routes.ProposalCtrl.list(group))

  def listBreadcrumb(user: User.Name, group: (Group.Slug, Group.Name)): Breadcrumb =
    group match {
      case (groupSlug, _) => GroupCtrl.breadcrumb(user, group).add("Proposals" -> routes.ProposalCtrl.list(groupSlug))
    }

  def header(group: Group.Slug): HeaderInfo =
    listHeader(group)

  def breadcrumb(user: User.Name, group: (Group.Slug, Group.Name), proposal: (Proposal.Id, Talk.Title)): Breadcrumb =
    (group, proposal) match {
      case ((groupSlug, _), (proposalId, proposalTitle)) =>
        listBreadcrumb(user, group).add(proposalTitle.value -> routes.ProposalCtrl.detail(groupSlug, proposalId))
    }
}
