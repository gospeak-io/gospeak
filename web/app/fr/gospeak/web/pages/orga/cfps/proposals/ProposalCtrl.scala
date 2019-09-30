package fr.gospeak.web.pages.orga.cfps.proposals

import java.time.Instant

import cats.data.OptionT
import cats.effect.IO
import com.mohiva.play.silhouette.api.Silhouette
import com.mohiva.play.silhouette.api.actions.SecuredRequest
import fr.gospeak.core.domain._
import fr.gospeak.core.services.storage._
import fr.gospeak.infra.services.EmailSrv
import fr.gospeak.libs.scalautils.Extensions._
import fr.gospeak.libs.scalautils.domain.{Page, Slides, Video}
import fr.gospeak.web.auth.domain.CookieEnv
import fr.gospeak.web.domain.Breadcrumb
import fr.gospeak.web.emails.Emails
import fr.gospeak.web.pages.orga.cfps.CfpCtrl
import fr.gospeak.web.pages.orga.cfps.proposals.ProposalCtrl._
import fr.gospeak.web.pages.speaker.talks.proposals.ProposalForms
import fr.gospeak.web.utils.{GenericForm, HttpUtils, UICtrl}
import play.api.data.Form
import play.api.mvc.{Action, AnyContent, ControllerComponents, Result}

import scala.util.control.NonFatal

class ProposalCtrl(cc: ControllerComponents,
                   silhouette: Silhouette[CookieEnv],
                   userRepo: OrgaUserRepo,
                   userRequestRepo: OrgaUserRequestRepo,
                   groupRepo: OrgaGroupRepo,
                   cfpRepo: OrgaCfpRepo,
                   eventRepo: OrgaEventRepo,
                   proposalRepo: OrgaProposalRepo,
                   emailSrv: EmailSrv) extends UICtrl(cc, silhouette) {

  import silhouette._

  def list(group: Group.Slug, cfp: Cfp.Slug, params: Page.Params): Action[AnyContent] = SecuredAction.async { implicit req =>
    (for {
      groupElt <- OptionT(groupRepo.find(user, group))
      cfpElt <- OptionT(cfpRepo.find(groupElt.id, cfp))
      proposals <- OptionT.liftF(proposalRepo.listFull(cfpElt.id, params))
      speakers <- OptionT.liftF(userRepo.list(proposals.items.flatMap(_.users).distinct))
      b = listBreadcrumb(groupElt, cfpElt)
    } yield Ok(html.list(groupElt, cfpElt, proposals, speakers)(b))).value.map(_.getOrElse(cfpNotFound(group, cfp))).unsafeToFuture()
  }

  def detail(group: Group.Slug, cfp: Cfp.Slug, proposal: Proposal.Id): Action[AnyContent] = SecuredAction.async { implicit req =>
    (for {
      groupElt <- OptionT(groupRepo.find(user, group))
      cfpElt <- OptionT(cfpRepo.find(groupElt.id, cfp))
      proposalElt <- OptionT(proposalRepo.find(cfp, proposal))
      speakers <- OptionT.liftF(userRepo.list(proposalElt.users))
      invites <- OptionT.liftF(userRequestRepo.listPendingInvites(proposal))
      events <- OptionT.liftF(eventRepo.list(proposalElt.event.toList))
      b = breadcrumb(groupElt, cfpElt, proposalElt)
      res = Ok(html.detail(groupElt, cfpElt, proposalElt, speakers, invites, events, ProposalForms.addSpeaker, GenericForm.embed)(b))
    } yield res).value.map(_.getOrElse(proposalNotFound(group, cfp, proposal))).unsafeToFuture()
  }

  def edit(group: Group.Slug, cfp: Cfp.Slug, proposal: Proposal.Id): Action[AnyContent] = SecuredAction.async { implicit req =>
    editForm(group, cfp, proposal, ProposalForms.create).unsafeToFuture()
  }

  def doEdit(group: Group.Slug, cfp: Cfp.Slug, proposal: Proposal.Id): Action[AnyContent] = SecuredAction.async { implicit req =>
    val now = Instant.now()
    ProposalForms.create.bindFromRequest.fold(
      formWithErrors => editForm(group, cfp, proposal, formWithErrors),
      data => proposalRepo.edit(user, group, cfp, proposal)(data, now).map { _ => Redirect(routes.ProposalCtrl.detail(group, cfp, proposal)) }
    ).unsafeToFuture()
  }

  private def editForm(group: Group.Slug, cfp: Cfp.Slug, proposal: Proposal.Id, form: Form[Proposal.Data])(implicit req: SecuredRequest[CookieEnv, AnyContent]): IO[Result] = {
    (for {
      groupElt <- OptionT(groupRepo.find(by, group))
      cfpElt <- OptionT(cfpRepo.find(groupElt.id, cfp))
      proposalElt <- OptionT(proposalRepo.find(cfp, proposal))
      b = breadcrumb(groupElt, cfpElt, proposalElt).add("Edit" -> routes.ProposalCtrl.edit(group, cfp, proposal))
      filledForm = if (form.hasErrors) form else form.fill(proposalElt.data)
    } yield Ok(html.edit(groupElt, cfpElt, proposalElt, filledForm)(b))).value.map(_.getOrElse(proposalNotFound(group, cfp, proposal)))
  }

  def inviteSpeaker(group: Group.Slug, cfp: Cfp.Slug, proposal: Proposal.Id): Action[AnyContent] = SecuredAction.async { implicit req =>
    val now = Instant.now()
    val next = Redirect(routes.ProposalCtrl.detail(group, cfp, proposal))
    ProposalForms.addSpeaker.bindFromRequest.fold(
      formWithErrors => IO.pure(next.flashing(formWithErrors.errors.map(e => "error" -> e.format): _*)),
      email => (for {
        proposalElt <- OptionT(proposalRepo.find(cfp, proposal))
        invite <- OptionT.liftF(userRequestRepo.invite(proposalElt.id, email, user, now))
        _ <- OptionT.liftF(emailSrv.send(Emails.inviteSpeakerToProposal(invite, proposalElt, req.identity.user)))
      } yield next.flashing("success" -> s"<b>$email</b> is invited as speaker")).value.map(_.getOrElse(proposalNotFound(group, cfp, proposal)))
    ).unsafeToFuture()
  }

  def cancelInviteSpeaker(group: Group.Slug, cfp: Cfp.Slug, proposal: Proposal.Id, request: UserRequest.Id): Action[AnyContent] = SecuredAction.async { implicit req =>
    val now = Instant.now()
    (for {
      proposalElt <- OptionT(proposalRepo.find(cfp, proposal))
      invite <- OptionT.liftF(userRequestRepo.cancelProposalInvite(request, user, now))
      _ <- OptionT.liftF(emailSrv.send(Emails.inviteSpeakerToProposalCanceled(invite, proposalElt, req.identity.user)))
      next = Redirect(routes.ProposalCtrl.detail(group, cfp, proposal)).flashing("success" -> s"Invitation to <b>${invite.email.value}</b> has been canceled")
    } yield next).value.map(_.getOrElse(proposalNotFound(group, cfp, proposal))).unsafeToFuture()
  }

  def removeSpeaker(group: Group.Slug, cfp: Cfp.Slug, proposal: Proposal.Id, speaker: User.Slug): Action[AnyContent] = SecuredAction.async { implicit req =>
    val now = Instant.now()
    val next = Redirect(routes.ProposalCtrl.detail(group, cfp, proposal))
    (for {
      proposalElt <- OptionT(proposalRepo.find(cfp, proposal))
      speakerElt <- OptionT(userRepo.find(speaker))
      res <- OptionT.liftF {
        proposalRepo.removeSpeaker(cfp, proposal)(speakerElt.id, user, now).flatMap { _ =>
          if (speakerElt.id == user) IO.pure(next.flashing("success" -> s"You removed yourself from this proposal"))
          else emailSrv.send(Emails.speakerRemovedFromProposal(proposalElt, speakerElt, req.identity.user))
            .map(_ => next.flashing("success" -> s"<b>${speakerElt.name.value}</b> removed from speakers"))
        }.recover { case NonFatal(e) => next.flashing("error" -> s"<b>${speakerElt.name.value}</b> not removed: ${e.getMessage}") }
      }
    } yield res).value.map(_.getOrElse(proposalNotFound(group, cfp, proposal))).unsafeToFuture()
  }

  def doAddSlides(group: Group.Slug, cfp: Cfp.Slug, proposal: Proposal.Id): Action[AnyContent] = SecuredAction.async { implicit req =>
    val now = Instant.now()
    val next = Redirect(routes.ProposalCtrl.detail(group, cfp, proposal))
    GenericForm.embed.bindFromRequest.fold(
      formWithErrors => IO.pure(next.flashing(formWithErrors.errors.map(e => "error" -> e.format): _*)),
      data => Slides.from(data) match {
        case Left(err) => IO.pure(next.flashing(err.errors.map(e => "error" -> e.value): _*))
        case Right(slides) => proposalRepo.editSlides(cfp, proposal)(slides, by, now).map(_ => next)
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
        case Right(video) => proposalRepo.editVideo(cfp, proposal)(video, by, now).map(_ => next)
      }
    ).unsafeToFuture()
  }

  def reject(group: Group.Slug, cfp: Cfp.Slug, proposal: Proposal.Id): Action[AnyContent] = SecuredAction.async { implicit req =>
    val now = Instant.now()
    val next = Redirect(HttpUtils.getReferer(req).getOrElse(routes.ProposalCtrl.detail(group, cfp, proposal).toString))
    (for {
      groupElt <- OptionT(groupRepo.find(by, group))
      cfpElt <- OptionT(cfpRepo.find(groupElt.id, cfp))
      proposalElt <- OptionT(proposalRepo.find(cfp, proposal))
      _ <- OptionT.liftF(proposalRepo.reject(cfp, proposal, by, now))
    } yield next).value.map(_.getOrElse(proposalNotFound(group, cfp, proposal))).unsafeToFuture()
  }

  def cancelRejection(group: Group.Slug, cfp: Cfp.Slug, proposal: Proposal.Id): Action[AnyContent] = SecuredAction.async { implicit req =>
    val now = Instant.now()
    val next = Redirect(HttpUtils.getReferer(req).getOrElse(routes.ProposalCtrl.detail(group, cfp, proposal).toString))
    (for {
      groupElt <- OptionT(groupRepo.find(by, group))
      cfpElt <- OptionT(cfpRepo.find(groupElt.id, cfp))
      proposalElt <- OptionT(proposalRepo.find(cfp, proposal))
      _ <- OptionT.liftF(proposalRepo.cancelReject(cfp, proposal, by, now))
    } yield next).value.map(_.getOrElse(proposalNotFound(group, cfp, proposal))).unsafeToFuture()
  }
}

object ProposalCtrl {
  def listBreadcrumb(group: Group, cfp: Cfp): Breadcrumb =
    CfpCtrl.breadcrumb(group, cfp).add("Proposals" -> routes.ProposalCtrl.list(group.slug, cfp.slug))

  def breadcrumb(group: Group, cfp: Cfp, proposal: Proposal): Breadcrumb =
    listBreadcrumb(group, cfp).add(proposal.title.value -> routes.ProposalCtrl.detail(group.slug, cfp.slug, proposal.id))
}
