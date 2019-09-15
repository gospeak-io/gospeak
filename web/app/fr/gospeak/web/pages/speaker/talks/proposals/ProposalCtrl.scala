package fr.gospeak.web.pages.speaker.talks.proposals

import java.time.{Instant, LocalDateTime}

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
import fr.gospeak.web.domain.{Breadcrumb, GospeakMessageBus}
import fr.gospeak.web.emails.Emails
import fr.gospeak.web.pages.speaker.talks.TalkCtrl
import fr.gospeak.web.pages.speaker.talks.cfps.CfpCtrl
import fr.gospeak.web.pages.speaker.talks.cfps.routes.{CfpCtrl => CfpRoutes}
import fr.gospeak.web.pages.speaker.talks.proposals.ProposalCtrl._
import fr.gospeak.web.pages.user.routes.{UserCtrl => UserRoutes}
import fr.gospeak.web.utils.{GenericForm, UICtrl}
import play.api.data.Form
import play.api.mvc._

import scala.util.control.NonFatal

class ProposalCtrl(cc: ControllerComponents,
                   silhouette: Silhouette[CookieEnv],
                   userRepo: SpeakerUserRepo,
                   userRequestRepo: SpeakerUserRequestRepo,
                   groupRepo: SpeakerGroupRepo,
                   cfpRepo: SpeakerCfpRepo,
                   eventRepo: SpeakerEventRepo,
                   talkRepo: SpeakerTalkRepo,
                   proposalRepo: SpeakerProposalRepo,
                   emailSrv: EmailSrv,
                   mb: GospeakMessageBus) extends UICtrl(cc, silhouette) {

  import silhouette._

  def list(talk: Talk.Slug, params: Page.Params): Action[AnyContent] = SecuredAction.async { implicit req =>
    (for {
      talkElt <- OptionT(talkRepo.find(user, talk))
      proposals <- OptionT.liftF(proposalRepo.list(talkElt.id, params))
      events <- OptionT.liftF(eventRepo.list(proposals.items.flatMap(_._2.event)))
      b = listBreadcrumb(req.identity.user, talkElt)
    } yield Ok(html.list(talkElt, proposals, events)(b))).value.map(_.getOrElse(talkNotFound(talk))).unsafeToFuture()
  }

  def create(talk: Talk.Slug, cfp: Cfp.Slug): Action[AnyContent] = SecuredAction.async { implicit req =>
    createForm(ProposalForms.create, talk, cfp).unsafeToFuture()
  }

  def doCreate(talk: Talk.Slug, cfp: Cfp.Slug): Action[AnyContent] = SecuredAction.async { implicit req =>
    val now = Instant.now()
    val nowLDT = LocalDateTime.now()
    ProposalForms.create.bindFromRequest.fold(
      formWithErrors => createForm(formWithErrors, talk, cfp),
      data => {
        (for {
          talkElt <- OptionT(talkRepo.find(user, talk))
          cfpElt <- OptionT(cfpRepo.find(cfp))
          proposalElt <- OptionT.liftF(proposalRepo.create(talkElt.id, cfpElt.id, data, talkElt.speakers, by, now))
          groupElt <- OptionT(groupRepo.find(cfpElt.group))
          _ <- OptionT.liftF(mb.publishProposalCreated(groupElt, cfpElt, proposalElt, nowLDT))
          msg = s"Well done! Your proposal <b>${proposalElt.title.value}</b> is proposed to <b>${cfpElt.name.value}</b>"
        } yield Redirect(routes.ProposalCtrl.detail(talk, cfp)).flashing("success" -> msg)).value.map(_.getOrElse(cfpNotFound(talk, cfp)))
      }
    ).unsafeToFuture()
  }

  private def createForm(form: Form[Proposal.Data], talk: Talk.Slug, cfp: Cfp.Slug)(implicit req: SecuredRequest[CookieEnv, AnyContent]): IO[Result] = {
    (for {
      talkElt <- OptionT(talkRepo.find(user, talk))
      cfpElt <- OptionT(cfpRepo.find(cfp))
      proposalOpt <- OptionT.liftF(proposalRepo.find(user, talk, cfp))
      filledForm = if (form.hasErrors) form else form.fill(Proposal.Data(talkElt))
      b = CfpCtrl.listBreadcrumb(req.identity.user, talkElt).add(cfpElt.name.value -> CfpRoutes.list(talkElt.slug))
    } yield proposalOpt
      .map(_ => Redirect(routes.ProposalCtrl.detail(talk, cfp)))
      .getOrElse(Ok(html.create(filledForm, talkElt, cfpElt)(b)))).value.map(_.getOrElse(cfpNotFound(talk, cfp)))
  }

  def detail(talk: Talk.Slug, cfp: Cfp.Slug): Action[AnyContent] = SecuredAction.async { implicit req =>
    (for {
      proposalFull <- OptionT(proposalRepo.findWithCfpTalkEvent(talk, cfp)(user))
      invites <- OptionT.liftF(userRequestRepo.listPendingInvites(proposalFull.proposal.id))
      speakers <- OptionT.liftF(userRepo.list(proposalFull.proposal.users))
      b = breadcrumb(req.identity.user, proposalFull.talk, proposalFull.cfp)
      res = Ok(html.detail(proposalFull.cfp, proposalFull.talk, proposalFull.proposal, proposalFull.event, speakers, invites, ProposalForms.addSpeaker, GenericForm.embed)(b))
    } yield res).value.map(_.getOrElse(proposalNotFound(talk, cfp))).unsafeToFuture()
  }

  def edit(talk: Talk.Slug, cfp: Cfp.Slug): Action[AnyContent] = SecuredAction.async { implicit req =>
    editForm(talk, cfp, ProposalForms.create).unsafeToFuture()
  }

  def doEdit(talk: Talk.Slug, cfp: Cfp.Slug): Action[AnyContent] = SecuredAction.async { implicit req =>
    val now = Instant.now()
    ProposalForms.create.bindFromRequest.fold(
      formWithErrors => editForm(talk, cfp, formWithErrors),
      data => proposalRepo.edit(talk, cfp)(data, by, now).map { _ => Redirect(routes.ProposalCtrl.detail(talk, cfp)) }
    ).unsafeToFuture()
  }

  private def editForm(talk: Talk.Slug, cfp: Cfp.Slug, form: Form[Proposal.Data])(implicit req: SecuredRequest[CookieEnv, AnyContent]): IO[Result] = {
    (for {
      proposalFull <- OptionT(proposalRepo.findWithCfpTalkEvent(talk, cfp)(user))
      b = breadcrumb(req.identity.user, proposalFull.talk, proposalFull.cfp).add("Edit" -> routes.ProposalCtrl.edit(talk, cfp))
      filledForm = if (form.hasErrors) form else form.fill(proposalFull.proposal.data)
    } yield Ok(html.edit(filledForm, proposalFull.talk, proposalFull.cfp, proposalFull.proposal)(b))).value.map(_.getOrElse(talkNotFound(talk)))
  }

  def inviteSpeaker(talk: Talk.Slug, cfp: Cfp.Slug): Action[AnyContent] = SecuredAction.async { implicit req =>
    val now = Instant.now()
    val next = Redirect(routes.ProposalCtrl.detail(talk, cfp))
    ProposalForms.addSpeaker.bindFromRequest.fold(
      formWithErrors => IO.pure(next.flashing(formWithErrors.errors.map(e => "error" -> e.format): _*)),
      email => (for {
        proposalElt <- OptionT(proposalRepo.find(user, talk, cfp))
        invite <- OptionT.liftF(userRequestRepo.invite(proposalElt.id, email, user, now))
        _ <- OptionT.liftF(emailSrv.send(Emails.inviteSpeakerToProposal(invite, proposalElt, req.identity.user)))
      } yield next.flashing("success" -> s"<b>$email</b> is invited as speaker")).value.map(_.getOrElse(proposalNotFound(talk, cfp)))
    ).unsafeToFuture()
  }

  def cancelInviteSpeaker(talk: Talk.Slug, cfp: Cfp.Slug, request: UserRequest.Id): Action[AnyContent] = SecuredAction.async { implicit req =>
    val now = Instant.now()
    (for {
      proposalElt <- OptionT(proposalRepo.find(user, talk, cfp))
      invite <- OptionT.liftF(userRequestRepo.cancelProposalInvite(request, user, now))
      _ <- OptionT.liftF(emailSrv.send(Emails.inviteSpeakerToProposalCanceled(invite, proposalElt, req.identity.user)))
      next = Redirect(routes.ProposalCtrl.detail(talk, cfp)).flashing("success" -> s"Invitation to <b>${invite.email.value}</b> has been canceled")
    } yield next).value.map(_.getOrElse(proposalNotFound(talk, cfp))).unsafeToFuture()
  }

  def removeSpeaker(talk: Talk.Slug, cfp: Cfp.Slug, speaker: User.Slug): Action[AnyContent] = SecuredAction.async { implicit req =>
    val now = Instant.now()
    val next = Redirect(routes.ProposalCtrl.detail(talk, cfp))
    (for {
      proposalElt <- OptionT(proposalRepo.find(user, talk, cfp))
      speakerElt <- OptionT(userRepo.find(speaker))
      res <- OptionT.liftF {
        proposalRepo.removeSpeaker(talk, cfp)(speakerElt.id, user, now).flatMap { _ =>
          if (speakerElt.id == user) IO.pure(Redirect(UserRoutes.index()).flashing("success" -> s"You removed yourself from <b>$talk</b> talk"))
          else emailSrv.send(Emails.speakerRemovedFromProposal(proposalElt, speakerElt, req.identity.user))
            .map(_ => next.flashing("success" -> s"<b>${speakerElt.name.value}</b> removed from speakers"))
        }.recover { case NonFatal(e) => next.flashing("error" -> s"<b>${speakerElt.name.value}</b> not removed: ${e.getMessage}") }
      }
    } yield res).value.map(_.getOrElse(proposalNotFound(talk, cfp))).unsafeToFuture()
  }

  def doAddSlides(talk: Talk.Slug, cfp: Cfp.Slug): Action[AnyContent] = SecuredAction.async { implicit req =>
    val now = Instant.now()
    val next = Redirect(routes.ProposalCtrl.detail(talk, cfp))
    GenericForm.embed.bindFromRequest.fold(
      formWithErrors => IO.pure(next.flashing(formWithErrors.errors.map(e => "error" -> e.format): _*)),
      data => Slides.from(data) match {
        case Left(err) => IO.pure(next.flashing(err.errors.map(e => "error" -> e.value): _*))
        case Right(slides) => proposalRepo.editSlides(talk, cfp)(slides, by, now).map(_ => next)
      }
    ).unsafeToFuture()
  }

  def doAddVideo(talk: Talk.Slug, cfp: Cfp.Slug): Action[AnyContent] = SecuredAction.async { implicit req =>
    val now = Instant.now()
    val next = Redirect(routes.ProposalCtrl.detail(talk, cfp))
    GenericForm.embed.bindFromRequest.fold(
      formWithErrors => IO.pure(next.flashing(formWithErrors.errors.map(e => "error" -> e.format): _*)),
      data => Video.from(data) match {
        case Left(err) => IO.pure(next.flashing(err.errors.map(e => "error" -> e.value): _*))
        case Right(video) => proposalRepo.editVideo(talk, cfp)(video, by, now).map(_ => next)
      }
    ).unsafeToFuture()
  }
}

object ProposalCtrl {
  def listBreadcrumb(user: User, talk: Talk): Breadcrumb =
    TalkCtrl.breadcrumb(user, talk).addOpt("Proposals" -> Some(routes.ProposalCtrl.list(talk.slug)).filter(_ => talk.hasSpeaker(user.id)))

  def breadcrumb(user: User, talk: Talk, cfp: Cfp): Breadcrumb =
    listBreadcrumb(user, talk).add(cfp.name.value -> CfpRoutes.list(talk.slug))
}
