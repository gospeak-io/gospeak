package fr.gospeak.web.pages.orga.cfps.proposals

import java.time.Instant

import cats.data.OptionT
import cats.effect.IO
import com.mohiva.play.silhouette.api.Silhouette
import com.mohiva.play.silhouette.api.actions.SecuredRequest
import fr.gospeak.core.domain.{Cfp, Group, Proposal}
import fr.gospeak.core.services._
import fr.gospeak.libs.scalautils.domain.{Page, Slides, Video}
import fr.gospeak.web.auth.domain.CookieEnv
import fr.gospeak.web.domain.Breadcrumb
import fr.gospeak.web.pages.orga.cfps.CfpCtrl
import fr.gospeak.web.pages.orga.cfps.proposals.ProposalCtrl._
import fr.gospeak.web.pages.speaker.talks.proposals.ProposalForms
import fr.gospeak.web.utils.{GenericForm, UICtrl}
import play.api.data.Form
import play.api.mvc.{Action, AnyContent, ControllerComponents, Result}

class ProposalCtrl(cc: ControllerComponents,
                   silhouette: Silhouette[CookieEnv],
                   userRepo: OrgaUserRepo,
                   groupRepo: OrgaGroupRepo,
                   cfpRepo: OrgaCfpRepo,
                   eventRepo: OrgaEventRepo,
                   proposalRepo: OrgaProposalRepo) extends UICtrl(cc, silhouette) {

  import silhouette._

  def list(group: Group.Slug, cfp: Cfp.Slug, params: Page.Params): Action[AnyContent] = SecuredAction.async { implicit req =>
    (for {
      groupElt <- OptionT(groupRepo.find(req.identity.user.id, group))
      cfpElt <- OptionT(cfpRepo.find(groupElt.id, cfp))
      proposals <- OptionT.liftF(proposalRepo.list(cfpElt.id, params))
      speakers <- OptionT.liftF(userRepo.list(proposals.items.flatMap(_.speakers.toList).distinct))
      events <- OptionT.liftF(eventRepo.list(proposals.items.flatMap(_.event.toList).distinct))
      b = listBreadcrumb(groupElt, cfpElt)
    } yield Ok(html.list(groupElt, cfpElt, proposals, speakers, events)(b))).value.map(_.getOrElse(cfpNotFound(group, cfp))).unsafeToFuture()
  }

  def detail(group: Group.Slug, cfp: Cfp.Slug, proposal: Proposal.Id): Action[AnyContent] = SecuredAction.async { implicit req =>
    (for {
      groupElt <- OptionT(groupRepo.find(req.identity.user.id, group))
      cfpElt <- OptionT(cfpRepo.find(groupElt.id, cfp))
      proposalElt <- OptionT(proposalRepo.find(cfp, proposal))
      speakers <- OptionT.liftF(userRepo.list(proposalElt.speakers.toList))
      events <- OptionT.liftF(eventRepo.list(proposalElt.event.toList))
      b = breadcrumb(groupElt, cfpElt, proposalElt)
    } yield Ok(html.detail(groupElt, cfpElt, proposalElt, speakers, events, GenericForm.embed)(b))).value.map(_.getOrElse(proposalNotFound(group, cfp, proposal))).unsafeToFuture()
  }

  def edit(group: Group.Slug, cfp: Cfp.Slug, proposal: Proposal.Id): Action[AnyContent] = SecuredAction.async { implicit req =>
    editForm(group, cfp, proposal, ProposalForms.create).unsafeToFuture()
  }

  def doEdit(group: Group.Slug, cfp: Cfp.Slug, proposal: Proposal.Id): Action[AnyContent] = SecuredAction.async { implicit req =>
    val now = Instant.now()
    ProposalForms.create.bindFromRequest.fold(
      formWithErrors => editForm(group, cfp, proposal, formWithErrors),
      data => proposalRepo.edit(req.identity.user.id, group, cfp, proposal)(data, now).map { _ => Redirect(routes.ProposalCtrl.detail(group, cfp, proposal)) }
    ).unsafeToFuture()
  }

  private def editForm(group: Group.Slug, cfp: Cfp.Slug, proposal: Proposal.Id, form: Form[Proposal.Data])(implicit req: SecuredRequest[CookieEnv, AnyContent]): IO[Result] = {
    (for {
      groupElt <- OptionT(groupRepo.find(req.identity.user.id, group))
      cfpElt <- OptionT(cfpRepo.find(groupElt.id, cfp))
      proposalElt <- OptionT(proposalRepo.find(cfp, proposal))
      b = breadcrumb(groupElt, cfpElt, proposalElt).add("Edit" -> routes.ProposalCtrl.edit(group, cfp, proposal))
      filledForm = if (form.hasErrors) form else form.fill(proposalElt.data)
    } yield Ok(html.edit(groupElt, cfpElt, proposalElt, filledForm)(b))).value.map(_.getOrElse(proposalNotFound(group, cfp, proposal)))
  }

  def doAddSlides(group: Group.Slug, cfp: Cfp.Slug, proposal: Proposal.Id): Action[AnyContent] = SecuredAction.async { implicit req =>
    val now = Instant.now()
    val next = Redirect(routes.ProposalCtrl.detail(group, cfp, proposal))
    GenericForm.embed.bindFromRequest.fold(
      formWithErrors => IO.pure(next.flashing(formWithErrors.errors.map(e => "error" -> e.format): _*)),
      data => Slides.from(data) match {
        case Left(err) => IO.pure(next.flashing(err.errors.map(e => "error" -> e.value): _*))
        case Right(slides) => proposalRepo.editSlides(cfp, proposal)(slides, now, req.identity.user.id).map(_ => next)
      }
    ).unsafeToFuture()
  }

  def doAddVideo(group: Group.Slug, cfp: Cfp.Slug, proposal: Proposal.Id): Action[AnyContent] = SecuredAction.async { implicit req =>
    val now = Instant.now()
    val next = Redirect(routes.ProposalCtrl.detail(group, cfp, proposal))
    GenericForm.embed.bindFromRequest.fold(
      formWithErrors => IO.pure(next.flashing(formWithErrors.errors.map(e => "error" -> e.format): _*)),
      data => Video.from(data) match {
        case Left(err) => IO.pure(next.flashing(err.errors.map(e => "error" -> e.value): _*))
        case Right(video) => proposalRepo.editVideo(cfp, proposal)(video, now, req.identity.user.id).map(_ => next)
      }
    ).unsafeToFuture()
  }
}

object ProposalCtrl {
  def listBreadcrumb(group: Group, cfp: Cfp): Breadcrumb =
    CfpCtrl.breadcrumb(group, cfp).add("Proposals" -> routes.ProposalCtrl.list(group.slug, cfp.slug))

  def breadcrumb(group: Group, cfp: Cfp, proposal: Proposal): Breadcrumb =
    listBreadcrumb(group, cfp).add(proposal.title.value -> routes.ProposalCtrl.detail(group.slug, cfp.slug, proposal.id))
}
