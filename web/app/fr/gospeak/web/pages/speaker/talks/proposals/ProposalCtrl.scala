package fr.gospeak.web.pages.speaker.talks.proposals

import cats.data.OptionT
import cats.effect.IO
import com.mohiva.play.silhouette.api.Silhouette
import fr.gospeak.core.ApplicationConf
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
import fr.gospeak.web.utils.{GenericForm, UserReq, UICtrl}
import play.api.data.Form
import play.api.mvc._

import scala.util.control.NonFatal

class ProposalCtrl(cc: ControllerComponents,
                   silhouette: Silhouette[CookieEnv],
                   env: ApplicationConf.Env,
                   userRepo: SpeakerUserRepo,
                   userRequestRepo: SpeakerUserRequestRepo,
                   groupRepo: SpeakerGroupRepo,
                   cfpRepo: SpeakerCfpRepo,
                   eventRepo: SpeakerEventRepo,
                   talkRepo: SpeakerTalkRepo,
                   proposalRepo: SpeakerProposalRepo,
                   commentRepo: SpeakerCommentRepo,
                   emailSrv: EmailSrv,
                   mb: GospeakMessageBus) extends UICtrl(cc, silhouette, env) {
  def list(talk: Talk.Slug, params: Page.Params): Action[AnyContent] = SecuredActionIO { implicit req =>
    (for {
      talkElt <- OptionT(talkRepo.find(req.user.id, talk))
      proposals <- OptionT.liftF(proposalRepo.listFull(talkElt.id, params))
      b = listBreadcrumb(req.user, talkElt)
    } yield Ok(html.list(talkElt, proposals)(b))).value.map(_.getOrElse(talkNotFound(talk)))
  }

  def create(talk: Talk.Slug, cfp: Cfp.Slug): Action[AnyContent] = SecuredActionIO { implicit req =>
    createForm(ProposalForms.create, talk, cfp)
  }

  def doCreate(talk: Talk.Slug, cfp: Cfp.Slug): Action[AnyContent] = SecuredActionIO { implicit req =>
    ProposalForms.create.bindFromRequest.fold(
      formWithErrors => createForm(formWithErrors, talk, cfp),
      data => {
        (for {
          talkElt <- OptionT(talkRepo.find(req.user.id, talk))
          cfpElt <- OptionT(cfpRepo.findRead(cfp))
          proposalElt <- OptionT.liftF(proposalRepo.create(talkElt.id, cfpElt.id, data, talkElt.speakers, req.user.id, req.now))
          groupElt <- OptionT(groupRepo.find(cfpElt.group))
          _ <- OptionT.liftF(mb.publishProposalCreated(groupElt, cfpElt, proposalElt))
          msg = s"Well done! Your proposal <b>${proposalElt.title.value}</b> is proposed to <b>${cfpElt.name.value}</b>"
        } yield Redirect(routes.ProposalCtrl.detail(talk, cfp)).flashing("success" -> msg)).value.map(_.getOrElse(cfpNotFound(talk, cfp)))
      }
    )
  }

  private def createForm(form: Form[Proposal.Data], talk: Talk.Slug, cfp: Cfp.Slug)(implicit req: UserReq[AnyContent]): IO[Result] = {
    (for {
      talkElt <- OptionT(talkRepo.find(req.user.id, talk))
      cfpElt <- OptionT(cfpRepo.findRead(cfp))
      proposalOpt <- OptionT.liftF(proposalRepo.find(req.user.id, talk, cfp))
      filledForm = if (form.hasErrors) form else form.fill(Proposal.Data(talkElt))
      b = CfpCtrl.listBreadcrumb(req.user, talkElt).add(cfpElt.name.value -> CfpRoutes.list(talkElt.slug))
    } yield proposalOpt
      .map(_ => Redirect(routes.ProposalCtrl.detail(talk, cfp)))
      .getOrElse(Ok(html.create(filledForm, talkElt, cfpElt)(b)))).value.map(_.getOrElse(cfpNotFound(talk, cfp)))
  }

  def detail(talk: Talk.Slug, cfp: Cfp.Slug): Action[AnyContent] = SecuredActionIO { implicit req =>
    proposalView(talk, cfp, GenericForm.comment)
  }

  def doSendComment(talk: Talk.Slug, cfp: Cfp.Slug): Action[AnyContent] = SecuredActionIO { implicit req =>
    GenericForm.comment.bindFromRequest.fold(
      formWithErrors => proposalView(talk, cfp, formWithErrors),
      data => (for {
        proposalElt <- OptionT(proposalRepo.find(req.user.id, talk, cfp))
        _ <- OptionT.liftF(commentRepo.addComment(proposalElt.id, data, req.user.id, req.now))
      } yield Redirect(routes.ProposalCtrl.detail(talk, cfp))).value.map(_.getOrElse(proposalNotFound(talk, cfp)))
    )
  }

  private def proposalView(talk: Talk.Slug, cfp: Cfp.Slug, commentForm: Form[Comment.Data])(implicit req: UserReq[AnyContent]): IO[Result] = {
    (for {
      proposalFull <- OptionT(proposalRepo.findFull(talk, cfp)(req.user.id))
      invites <- OptionT.liftF(userRequestRepo.listPendingInvites(proposalFull.id))
      speakers <- OptionT.liftF(userRepo.list(proposalFull.users))
      comments <- OptionT.liftF(commentRepo.getComments(proposalFull.id))
      b = breadcrumb(req.user, proposalFull)
      res = Ok(html.detail(proposalFull, speakers, comments, invites, ProposalForms.addSpeaker, GenericForm.embed, commentForm)(b))
    } yield res).value.map(_.getOrElse(proposalNotFound(talk, cfp)))
  }

  def edit(talk: Talk.Slug, cfp: Cfp.Slug): Action[AnyContent] = SecuredActionIO { implicit req =>
    editView(talk, cfp, ProposalForms.create)
  }

  def doEdit(talk: Talk.Slug, cfp: Cfp.Slug): Action[AnyContent] = SecuredActionIO { implicit req =>
    ProposalForms.create.bindFromRequest.fold(
      formWithErrors => editView(talk, cfp, formWithErrors),
      data => proposalRepo.edit(talk, cfp)(data, req.user.id, req.now).map(_ => Redirect(routes.ProposalCtrl.detail(talk, cfp)))
    )
  }

  private def editView(talk: Talk.Slug, cfp: Cfp.Slug, form: Form[Proposal.Data])(implicit req: UserReq[AnyContent]): IO[Result] = {
    (for {
      proposalFull <- OptionT(proposalRepo.findFull(talk, cfp)(req.user.id))
      b = breadcrumb(req.user, proposalFull).add("Edit" -> routes.ProposalCtrl.edit(talk, cfp))
      filledForm = if (form.hasErrors) form else form.fill(proposalFull.data)
    } yield Ok(html.edit(filledForm, proposalFull)(b))).value.map(_.getOrElse(talkNotFound(talk)))
  }

  def inviteSpeaker(talk: Talk.Slug, cfp: Cfp.Slug): Action[AnyContent] = SecuredActionIO { implicit req =>
    val next = Redirect(routes.ProposalCtrl.detail(talk, cfp))
    ProposalForms.addSpeaker.bindFromRequest.fold(
      formWithErrors => IO.pure(next.flashing("error" -> req.formatErrors(formWithErrors))),
      email => (for {
        proposalElt <- OptionT(proposalRepo.find(req.user.id, talk, cfp))
        invite <- OptionT.liftF(userRequestRepo.invite(proposalElt.id, email, req.user.id, req.now))
        _ <- OptionT.liftF(emailSrv.send(Emails.inviteSpeakerToProposal(invite, proposalElt)))
      } yield next.flashing("success" -> s"<b>$email</b> is invited as speaker")).value.map(_.getOrElse(proposalNotFound(talk, cfp)))
    )
  }

  def cancelInviteSpeaker(talk: Talk.Slug, cfp: Cfp.Slug, request: UserRequest.Id): Action[AnyContent] = SecuredActionIO { implicit req =>
    (for {
      proposalElt <- OptionT(proposalRepo.find(req.user.id, talk, cfp))
      invite <- OptionT.liftF(userRequestRepo.cancelProposalInvite(request, req.user.id, req.now))
      _ <- OptionT.liftF(emailSrv.send(Emails.inviteSpeakerToProposalCanceled(invite, proposalElt)))
      next = Redirect(routes.ProposalCtrl.detail(talk, cfp)).flashing("success" -> s"Invitation to <b>${invite.email.value}</b> has been canceled")
    } yield next).value.map(_.getOrElse(proposalNotFound(talk, cfp)))
  }

  def removeSpeaker(talk: Talk.Slug, cfp: Cfp.Slug, speaker: User.Slug): Action[AnyContent] = SecuredActionIO { implicit req =>
    val next = Redirect(routes.ProposalCtrl.detail(talk, cfp))
    (for {
      proposalElt <- OptionT(proposalRepo.find(req.user.id, talk, cfp))
      speakerElt <- OptionT(userRepo.find(speaker))
      res <- OptionT.liftF {
        proposalRepo.removeSpeaker(talk, cfp)(speakerElt.id, req.user.id, req.now).flatMap { _ =>
          if (speakerElt.id == req.user.id) IO.pure(Redirect(UserRoutes.index()).flashing("success" -> s"You removed yourself from <b>$talk</b> proposal"))
          else emailSrv.send(Emails.speakerRemovedFromProposal(proposalElt, speakerElt))
            .map(_ => next.flashing("success" -> s"<b>${speakerElt.name.value}</b> removed from speakers"))
        }.recover { case NonFatal(e) => next.flashing("error" -> s"<b>${speakerElt.name.value}</b> not removed: ${e.getMessage}") }
      }
    } yield res).value.map(_.getOrElse(proposalNotFound(talk, cfp)))
  }

  def doAddSlides(talk: Talk.Slug, cfp: Cfp.Slug): Action[AnyContent] = SecuredActionIO { implicit req =>
    val next = Redirect(routes.ProposalCtrl.detail(talk, cfp))
    GenericForm.embed.bindFromRequest.fold(
      formWithErrors => IO.pure(next.flashing("error" -> req.formatErrors(formWithErrors))),
      data => Slides.from(data) match {
        case Left(err) => IO.pure(next.flashing("error" -> err.getMessage))
        case Right(slides) => proposalRepo.editSlides(talk, cfp)(slides, req.user.id, req.now).map(_ => next)
      }
    )
  }

  def doAddVideo(talk: Talk.Slug, cfp: Cfp.Slug): Action[AnyContent] = SecuredActionIO { implicit req =>
    val next = Redirect(routes.ProposalCtrl.detail(talk, cfp))
    GenericForm.embed.bindFromRequest.fold(
      formWithErrors => IO.pure(next.flashing("error" -> req.formatErrors(formWithErrors))),
      data => Video.from(data) match {
        case Left(err) => IO.pure(next.flashing("error" -> err.getMessage))
        case Right(video) => proposalRepo.editVideo(talk, cfp)(video, req.user.id, req.now).map(_ => next)
      }
    )
  }
}

object ProposalCtrl {
  def listBreadcrumb(user: User, talk: Talk): Breadcrumb =
    TalkCtrl.breadcrumb(user, talk).addOpt("Proposals" -> Some(routes.ProposalCtrl.list(talk.slug)).filter(_ => talk.hasSpeaker(user.id)))

  def breadcrumb(user: User, proposal: Proposal.Full): Breadcrumb =
    listBreadcrumb(user, proposal.talk).add(proposal.cfp.name.value -> CfpRoutes.list(proposal.talk.slug))
}
