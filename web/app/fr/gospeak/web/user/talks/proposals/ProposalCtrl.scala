package fr.gospeak.web.user.talks.proposals

import java.time.Instant

import cats.data.OptionT
import cats.effect.IO
import fr.gospeak.core.domain._
import fr.gospeak.core.services.GospeakDb
import fr.gospeak.libs.scalautils.domain.{Page, Slides, Video}
import fr.gospeak.web.auth.AuthService
import fr.gospeak.web.domain.Breadcrumb
import fr.gospeak.web.user.talks.{TalkCtrl, TalkForms}
import fr.gospeak.web.user.talks.proposals.ProposalCtrl._
import fr.gospeak.web.utils.{GenericForm, UICtrl}
import play.api.mvc._

class ProposalCtrl(cc: ControllerComponents, db: GospeakDb, auth: AuthService) extends UICtrl(cc) {
  def list(talk: Talk.Slug, params: Page.Params): Action[AnyContent] = Action.async { implicit req: Request[AnyContent] =>
    implicit val user: User = auth.authed()
    (for {
      talkElt <- OptionT(db.getTalk(user.id, talk))
      proposals <- OptionT.liftF(db.getProposals(talkElt.id, params))
      events <- OptionT.liftF(db.getEvents(proposals.items.flatMap(_._2.event)))
      h = TalkCtrl.header(talkElt.slug)
      b = listBreadcrumb(user.name, talk -> talkElt.title)
    } yield Ok(html.list(talkElt, proposals, events)(h, b))).value.map(_.getOrElse(talkNotFound(talk))).unsafeToFuture()
  }

  def detail(talk: Talk.Slug, proposal: Proposal.Id): Action[AnyContent] = Action.async { implicit req: Request[AnyContent] =>
    implicit val user: User = auth.authed()
    (for {
      talkElt <- OptionT(db.getTalk(user.id, talk))
      proposalElt <- OptionT(db.getProposal(proposal))
      cfpElt <- OptionT(db.getCfp(proposalElt.cfp))
      speakers <- OptionT.liftF(db.getUsers(proposalElt.speakers.toList))
      events <- OptionT.liftF(db.getEvents(proposalElt.event.toSeq))
      h = TalkCtrl.header(talkElt.slug)
      b = breadcrumb(user.name, talk -> talkElt.title, proposal -> cfpElt.name)
    } yield Ok(html.detail(talkElt, cfpElt, proposalElt, speakers, events, GenericForm.embed)(h, b))).value.map(_.getOrElse(proposalNotFound(talk, proposal))).unsafeToFuture()
  }

  def doAddSlides(talk: Talk.Slug, proposal: Proposal.Id): Action[AnyContent] = Action.async { implicit req: Request[AnyContent] =>
    implicit val user: User = auth.authed()
    val now = Instant.now()
    val next = Redirect(routes.ProposalCtrl.detail(talk, proposal))
    GenericForm.embed.bindFromRequest.fold(
      formWithErrors => IO.pure(next.flashing(formWithErrors.errors.map(e => "error" -> e.format): _*)),
      data => Slides.from(data) match {
        case Left(err) => IO.pure(next.flashing(err.errors.map(e => "error" -> e.value): _*))
        case Right(slides) => db.updateProposalSlides(proposal)(slides, now, user.id).map(_ => next)
      }
    ).unsafeToFuture()
  }

  def doAddVideo(talk: Talk.Slug, proposal: Proposal.Id): Action[AnyContent] = Action.async { implicit req: Request[AnyContent] =>
    implicit val user: User = auth.authed()
    val now = Instant.now()
    val next = Redirect(routes.ProposalCtrl.detail(talk, proposal))
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
