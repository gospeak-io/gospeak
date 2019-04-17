package fr.gospeak.web.pages.speaker.talks.proposals

import java.time.Instant

import cats.data.OptionT
import cats.effect.IO
import com.mohiva.play.silhouette.api.Silhouette
import com.mohiva.play.silhouette.api.actions.SecuredRequest
import fr.gospeak.core.domain._
import fr.gospeak.core.services._
import fr.gospeak.libs.scalautils.domain.{Page, Slides, Video}
import fr.gospeak.web.auth.domain.CookieEnv
import fr.gospeak.web.domain.Breadcrumb
import fr.gospeak.web.pages.speaker.talks.proposals.ProposalCtrl._
import fr.gospeak.web.pages.speaker.talks.TalkCtrl
import fr.gospeak.web.utils.{GenericForm, UICtrl}
import play.api.data.Form
import play.api.mvc._

class ProposalCtrl(cc: ControllerComponents,
                   silhouette: Silhouette[CookieEnv],
                   userRepo: SpeakerUserRepo,
                   cfpRepo: SpeakerCfpRepo,
                   eventRepo: SpeakerEventRepo,
                   talkRepo: SpeakerTalkRepo,
                   proposalRepo: SpeakerProposalRepo) extends UICtrl(cc, silhouette) {

  import silhouette._

  def list(talk: Talk.Slug, params: Page.Params): Action[AnyContent] = SecuredAction.async { implicit req =>
    (for {
      talkElt <- OptionT(talkRepo.find(req.identity.user.id, talk))
      proposals <- OptionT.liftF(proposalRepo.list(talkElt.id, params))
      events <- OptionT.liftF(eventRepo.list(proposals.items.flatMap(_._2.event)))
      b = listBreadcrumb(req.identity.user, talkElt)
    } yield Ok(html.list(talkElt, proposals, events)(b))).value.map(_.getOrElse(talkNotFound(talk))).unsafeToFuture()
  }

  // TODO create
  // TODO doCreate

  // TODO replace proposalId by cfpSlug
  def detail(talk: Talk.Slug, proposal: Proposal.Id): Action[AnyContent] = SecuredAction.async { implicit req =>
    (for {
      talkElt <- OptionT(talkRepo.find(req.identity.user.id, talk))
      proposalElt <- OptionT(proposalRepo.find(proposal))
      cfpElt <- OptionT(cfpRepo.find(proposalElt.cfp))
      speakers <- OptionT.liftF(userRepo.list(proposalElt.speakers.toList))
      events <- OptionT.liftF(eventRepo.list(proposalElt.event.toSeq))
      b = breadcrumb(req.identity.user, talkElt, proposal -> cfpElt.name)
    } yield Ok(html.detail(talkElt, cfpElt, proposalElt, speakers, events, GenericForm.embed)(b))).value.map(_.getOrElse(proposalNotFound(talk, proposal))).unsafeToFuture()
  }

  def edit(talk: Talk.Slug, proposal: Proposal.Id): Action[AnyContent] = SecuredAction.async { implicit req =>
    editForm(talk, proposal, ProposalForms.create).unsafeToFuture()
  }

  def doEdit(talk: Talk.Slug, proposal: Proposal.Id): Action[AnyContent] = SecuredAction.async { implicit req =>
    val now = Instant.now()
    ProposalForms.create.bindFromRequest.fold(
      formWithErrors => editForm(talk, proposal, formWithErrors),
      data => proposalRepo.edit(req.identity.user.id, proposal)(data, now).map { _ => Redirect(routes.ProposalCtrl.detail(talk, proposal)) }
    ).unsafeToFuture()
  }

  private def editForm(talk: Talk.Slug, proposal: Proposal.Id, form: Form[Proposal.Data])(implicit req: SecuredRequest[CookieEnv, AnyContent]): IO[Result] = {
    (for {
      talkElt <- OptionT(talkRepo.find(req.identity.user.id, talk))
      proposalElt <- OptionT(proposalRepo.find(proposal))
      cfpElt <- OptionT(cfpRepo.find(proposalElt.cfp))
      b = breadcrumb(req.identity.user, talkElt, proposal -> cfpElt.name).add("Edit" -> routes.ProposalCtrl.edit(talk, proposal))
      filledForm = if (form.hasErrors) form else form.fill(proposalElt.data)
    } yield Ok(html.edit(filledForm, talkElt, cfpElt, proposalElt)(b))).value.map(_.getOrElse(talkNotFound(talk)))
  }

  def doAddSlides(talk: Talk.Slug, proposal: Proposal.Id): Action[AnyContent] = SecuredAction.async { implicit req =>
    val now = Instant.now()
    val next = Redirect(routes.ProposalCtrl.detail(talk, proposal))
    GenericForm.embed.bindFromRequest.fold(
      formWithErrors => IO.pure(next.flashing(formWithErrors.errors.map(e => "error" -> e.format): _*)),
      data => Slides.from(data) match {
        case Left(err) => IO.pure(next.flashing(err.errors.map(e => "error" -> e.value): _*))
        case Right(slides) => proposalRepo.editSlides(proposal)(slides, now, req.identity.user.id).map(_ => next)
      }
    ).unsafeToFuture()
  }

  def doAddVideo(talk: Talk.Slug, proposal: Proposal.Id): Action[AnyContent] = SecuredAction.async { implicit req =>
    val now = Instant.now()
    val next = Redirect(routes.ProposalCtrl.detail(talk, proposal))
    GenericForm.embed.bindFromRequest.fold(
      formWithErrors => IO.pure(next.flashing(formWithErrors.errors.map(e => "error" -> e.format): _*)),
      data => Video.from(data) match {
        case Left(err) => IO.pure(next.flashing(err.errors.map(e => "error" -> e.value): _*))
        case Right(video) => proposalRepo.editVideo(proposal)(video, now, req.identity.user.id).map(_ => next)
      }
    ).unsafeToFuture()
  }
}

object ProposalCtrl {
  def listBreadcrumb(user: User, talk: Talk): Breadcrumb =
    TalkCtrl.breadcrumb(user, talk).add("Proposals" -> routes.ProposalCtrl.list(talk.slug))

  def breadcrumb(user: User, talk: Talk, proposal: (Proposal.Id, Cfp.Name)): Breadcrumb =
    listBreadcrumb(user, talk).add(proposal._2.value -> routes.ProposalCtrl.detail(talk.slug, proposal._1))
}
