package fr.gospeak.web.user.groups.proposals

import java.time.Instant

import cats.data.OptionT
import cats.effect.IO
import fr.gospeak.core.domain._
import fr.gospeak.core.services.GospeakDb
import fr.gospeak.libs.scalautils.domain.{Page, Slides, Video}
import fr.gospeak.web.auth.AuthService
import fr.gospeak.web.domain.{Breadcrumb, HeaderInfo, NavLink}
import fr.gospeak.web.user.groups.GroupCtrl
import fr.gospeak.web.user.groups.proposals.ProposalCtrl._
import fr.gospeak.web.utils.{GenericForm, UICtrl}
import play.api.mvc._

class ProposalCtrl(cc: ControllerComponents, db: GospeakDb, auth: AuthService) extends UICtrl(cc) {
  def list(group: Group.Slug, params: Page.Params): Action[AnyContent] = Action.async { implicit req: Request[AnyContent] =>
    implicit val user: User = auth.authed()
    (for {
      groupElt <- OptionT(db.getGroup(user.id, group))
      cfpOpt <- OptionT.liftF(db.getCfp(groupElt.id))
      proposals <- cfpOpt.map(cfpElt => OptionT.liftF(db.getProposals(cfpElt.id, params))).getOrElse(OptionT.pure[IO](Page.empty[Proposal](params)))
      speakers <- OptionT.liftF(db.getUsers(proposals.items.flatMap(_.speakers.toList)))
      events <- OptionT.liftF(db.getEvents(proposals.items.flatMap(_.event)))
      h = listHeader(group)
      b = listBreadcrumb(user.name, group -> groupElt.name)
    } yield Ok(html.list(groupElt, cfpOpt, proposals, speakers, events)(h, b))).value.map(_.getOrElse(groupNotFound(group))).unsafeToFuture()
  }

  def detail(group: Group.Slug, proposal: Proposal.Id): Action[AnyContent] = Action.async { implicit req: Request[AnyContent] =>
    implicit val user: User = auth.authed()
    (for {
      groupElt <- OptionT(db.getGroup(user.id, group))
      proposalElt <- OptionT(db.getProposal(proposal))
      speakers <- OptionT.liftF(db.getUsers(proposalElt.speakers.toList))
      events <- OptionT.liftF(db.getEvents(proposalElt.event.toSeq))
      h = header(group)
      b = breadcrumb(user.name, group -> groupElt.name, proposal -> proposalElt.title)
    } yield Ok(html.detail(groupElt, proposalElt, speakers, events, GenericForm.embed)(h, b))).value.map(_.getOrElse(proposalNotFound(group, proposal))).unsafeToFuture()
  }

  def doAddSlides(group: Group.Slug, proposal: Proposal.Id): Action[AnyContent] = Action.async { implicit req: Request[AnyContent] =>
    implicit val user: User = auth.authed()
    val now = Instant.now()
    val next = Redirect(routes.ProposalCtrl.detail(group, proposal))
    GenericForm.embed.bindFromRequest.fold(
      formWithErrors => IO.pure(next.flashing(formWithErrors.errors.map(e => "error" -> e.format): _*)),
      data => Slides.from(data) match {
        case Left(err) => IO.pure(next.flashing(err.errors.map(e => "error" -> e.value): _*))
        case Right(slides) => db.updateProposalSlides(proposal)(slides, now, user.id).map(_ => next)
      }
    ).unsafeToFuture()
  }

  def doAddVideo(group: Group.Slug, proposal: Proposal.Id): Action[AnyContent] = Action.async { implicit req: Request[AnyContent] =>
    implicit val user: User = auth.authed()
    val now = Instant.now()
    val next = Redirect(routes.ProposalCtrl.detail(group, proposal))
    GenericForm.embed.bindFromRequest.fold(
      formWithErrors => IO.pure(next.flashing(formWithErrors.errors.map(e => "error" -> e.format): _*)),
      data => Video.from(data) match {
        case Left(err) => IO.pure(next.flashing(err.errors.map(e => "error" -> e.value): _*))
        case Right(video) => db.updateProposalVideo(proposal)(video, now, user.id).map(_ => next)
      }
    ).unsafeToFuture()
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

  def breadcrumb(user: User.Name, group: (Group.Slug, Group.Name), proposal: (Proposal.Id, Talk.Title)): Breadcrumb =
    (group, proposal) match {
      case ((groupSlug, _), (proposalId, proposalTitle)) =>
        listBreadcrumb(user, group).add(proposalTitle.value -> routes.ProposalCtrl.detail(groupSlug, proposalId))
    }
}
