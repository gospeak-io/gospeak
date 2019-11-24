package fr.gospeak.web.pages.speaker.talks.proposals

import cats.data.OptionT
import cats.effect.IO
import com.mohiva.play.silhouette.api.Silhouette
import fr.gospeak.core.ApplicationConf
import fr.gospeak.core.domain._
import fr.gospeak.core.domain.utils.UserCtx
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
import fr.gospeak.web.utils.{GenericForm, UICtrl, UserReq}
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
                   mb: GospeakMessageBus) extends UICtrl(cc, silhouette, env) with UICtrl.UserAction {
  def list(talk: Talk.Slug, params: Page.Params): Action[AnyContent] = UserAction(implicit req => implicit ctx => {
    (for {
      talkElt <- OptionT(talkRepo.find(talk))
      proposals <- OptionT.liftF(proposalRepo.listFull(talkElt.id, params))
      b = listBreadcrumb(talkElt)
    } yield Ok(html.list(talkElt, proposals)(b))).value.map(_.getOrElse(talkNotFound(talk)))
  })

  def create(talk: Talk.Slug, cfp: Cfp.Slug): Action[AnyContent] = UserAction(implicit req => implicit ctx => {
    createForm(ProposalForms.create, talk, cfp)
  })

  def doCreate(talk: Talk.Slug, cfp: Cfp.Slug): Action[AnyContent] = UserAction(implicit req => implicit ctx => {
    ProposalForms.create.bindFromRequest.fold(
      formWithErrors => createForm(formWithErrors, talk, cfp),
      data => {
        (for {
          talkElt <- OptionT(talkRepo.find(talk))
          cfpElt <- OptionT(cfpRepo.findRead(cfp))
          proposalElt <- OptionT.liftF(proposalRepo.create(talkElt.id, cfpElt.id, data, talkElt.speakers))
          groupElt <- OptionT(groupRepo.find(cfpElt.group))
          _ <- OptionT.liftF(mb.publishProposalCreated(groupElt, cfpElt, proposalElt))
          msg = s"Well done! Your proposal <b>${proposalElt.title.value}</b> is proposed to <b>${cfpElt.name.value}</b>"
        } yield Redirect(routes.ProposalCtrl.detail(talk, cfp)).flashing("success" -> msg)).value.map(_.getOrElse(cfpNotFound(talk, cfp)))
      }
    )
  })

  private def createForm(form: Form[Proposal.Data], talk: Talk.Slug, cfp: Cfp.Slug)(implicit req: UserReq[AnyContent], ctx: UserCtx): IO[Result] = {
    (for {
      talkElt <- OptionT(talkRepo.find(talk))
      cfpElt <- OptionT(cfpRepo.findRead(cfp))
      proposalOpt <- OptionT.liftF(proposalRepo.find(talk, cfp))
      filledForm = if (form.hasErrors) form else form.fill(Proposal.Data(talkElt))
      b = CfpCtrl.listBreadcrumb(talkElt).add(cfpElt.name.value -> CfpRoutes.list(talkElt.slug))
    } yield proposalOpt
      .map(_ => Redirect(routes.ProposalCtrl.detail(talk, cfp)))
      .getOrElse(Ok(html.create(filledForm, talkElt, cfpElt)(b)))).value.map(_.getOrElse(cfpNotFound(talk, cfp)))
  }

  def detail(talk: Talk.Slug, cfp: Cfp.Slug): Action[AnyContent] = UserAction(implicit req => implicit ctx => {
    proposalView(talk, cfp, GenericForm.comment)
  })

  def doSendComment(talk: Talk.Slug, cfp: Cfp.Slug): Action[AnyContent] = UserAction(implicit req => implicit ctx => {
    GenericForm.comment.bindFromRequest.fold(
      formWithErrors => proposalView(talk, cfp, formWithErrors),
      data => (for {
        proposalElt <- OptionT(proposalRepo.find(talk, cfp))
        _ <- OptionT.liftF(commentRepo.addComment(proposalElt.id, data))
      } yield Redirect(routes.ProposalCtrl.detail(talk, cfp))).value.map(_.getOrElse(proposalNotFound(talk, cfp)))
    )
  })

  private def proposalView(talk: Talk.Slug, cfp: Cfp.Slug, commentForm: Form[Comment.Data])(implicit req: UserReq[AnyContent], ctx: UserCtx): IO[Result] = {
    (for {
      proposalFull <- OptionT(proposalRepo.findFull(talk, cfp))
      invites <- OptionT.liftF(userRequestRepo.listPendingInvites(proposalFull.id))
      speakers <- OptionT.liftF(userRepo.list(proposalFull.users))
      comments <- OptionT.liftF(commentRepo.getComments(proposalFull.id))
      b = breadcrumb(proposalFull)
      res = Ok(html.detail(proposalFull, speakers, comments, invites, ProposalForms.addSpeaker, GenericForm.embed, commentForm)(b))
    } yield res).value.map(_.getOrElse(proposalNotFound(talk, cfp)))
  }

  def edit(talk: Talk.Slug, cfp: Cfp.Slug): Action[AnyContent] = UserAction(implicit req => implicit ctx => {
    editView(talk, cfp, ProposalForms.create)
  })

  def doEdit(talk: Talk.Slug, cfp: Cfp.Slug): Action[AnyContent] = UserAction(implicit req => implicit ctx => {
    ProposalForms.create.bindFromRequest.fold(
      formWithErrors => editView(talk, cfp, formWithErrors),
      data => proposalRepo.edit(talk, cfp, data).map(_ => Redirect(routes.ProposalCtrl.detail(talk, cfp)))
    )
  })

  private def editView(talk: Talk.Slug, cfp: Cfp.Slug, form: Form[Proposal.Data])(implicit req: UserReq[AnyContent], ctx: UserCtx): IO[Result] = {
    (for {
      proposalFull <- OptionT(proposalRepo.findFull(talk, cfp))
      filledForm = if (form.hasErrors) form else form.fill(proposalFull.data)
      b = breadcrumb(proposalFull).add("Edit" -> routes.ProposalCtrl.edit(talk, cfp))
    } yield Ok(html.edit(filledForm, proposalFull)(b))).value.map(_.getOrElse(talkNotFound(talk)))
  }

  def inviteSpeaker(talk: Talk.Slug, cfp: Cfp.Slug): Action[AnyContent] = UserAction(implicit req => implicit ctx => {
    val next = Redirect(routes.ProposalCtrl.detail(talk, cfp))
    ProposalForms.addSpeaker.bindFromRequest.fold(
      formWithErrors => IO.pure(next.flashing("error" -> req.formatErrors(formWithErrors))),
      email => (for {
        proposalElt <- OptionT(proposalRepo.find(talk, cfp))
        invite <- OptionT.liftF(userRequestRepo.invite(proposalElt.id, email))
        _ <- OptionT.liftF(emailSrv.send(Emails.inviteSpeakerToProposal(invite, proposalElt)))
      } yield next.flashing("success" -> s"<b>$email</b> is invited as speaker")).value.map(_.getOrElse(proposalNotFound(talk, cfp)))
    )
  })

  def cancelInviteSpeaker(talk: Talk.Slug, cfp: Cfp.Slug, request: UserRequest.Id): Action[AnyContent] = UserAction(implicit req => implicit ctx => {
    (for {
      proposalElt <- OptionT(proposalRepo.find(talk, cfp))
      invite <- OptionT.liftF(userRequestRepo.cancelProposalInvite(request))
      _ <- OptionT.liftF(emailSrv.send(Emails.inviteSpeakerToProposalCanceled(invite, proposalElt)))
      next = Redirect(routes.ProposalCtrl.detail(talk, cfp)).flashing("success" -> s"Invitation to <b>${invite.email.value}</b> has been canceled")
    } yield next).value.map(_.getOrElse(proposalNotFound(talk, cfp)))
  })

  def removeSpeaker(talk: Talk.Slug, cfp: Cfp.Slug, speaker: User.Slug): Action[AnyContent] = UserAction(implicit req => implicit ctx => {
    val next = Redirect(routes.ProposalCtrl.detail(talk, cfp))
    (for {
      proposalElt <- OptionT(proposalRepo.find(talk, cfp))
      speakerElt <- OptionT(userRepo.find(speaker))
      res <- OptionT.liftF {
        proposalRepo.removeSpeaker(talk, cfp, speakerElt.id).flatMap { _ =>
          if (speakerElt.id == req.user.id) IO.pure(Redirect(UserRoutes.index()).flashing("success" -> s"You removed yourself from <b>$talk</b> proposal"))
          else emailSrv.send(Emails.speakerRemovedFromProposal(proposalElt, speakerElt))
            .map(_ => next.flashing("success" -> s"<b>${speakerElt.name.value}</b> removed from speakers"))
        }.recover { case NonFatal(e) => next.flashing("error" -> s"<b>${speakerElt.name.value}</b> not removed: ${e.getMessage}") }
      }
    } yield res).value.map(_.getOrElse(proposalNotFound(talk, cfp)))
  })

  def doAddSlides(talk: Talk.Slug, cfp: Cfp.Slug): Action[AnyContent] = UserAction(implicit req => implicit ctx => {
    val next = Redirect(routes.ProposalCtrl.detail(talk, cfp))
    GenericForm.embed.bindFromRequest.fold(
      formWithErrors => IO.pure(next.flashing("error" -> req.formatErrors(formWithErrors))),
      data => Slides.from(data) match {
        case Left(err) => IO.pure(next.flashing("error" -> err.getMessage))
        case Right(slides) => proposalRepo.editSlides(talk, cfp, slides).map(_ => next)
      }
    )
  })

  def doAddVideo(talk: Talk.Slug, cfp: Cfp.Slug): Action[AnyContent] = UserAction(implicit req => implicit ctx => {
    val next = Redirect(routes.ProposalCtrl.detail(talk, cfp))
    GenericForm.embed.bindFromRequest.fold(
      formWithErrors => IO.pure(next.flashing("error" -> req.formatErrors(formWithErrors))),
      data => Video.from(data) match {
        case Left(err) => IO.pure(next.flashing("error" -> err.getMessage))
        case Right(video) => proposalRepo.editVideo(talk, cfp, video).map(_ => next)
      }
    )
  })
}

object ProposalCtrl {
  def listBreadcrumb(talk: Talk)(implicit req: UserReq[AnyContent]): Breadcrumb =
    TalkCtrl.breadcrumb(talk).addOpt("Proposals" -> Some(routes.ProposalCtrl.list(talk.slug)).filter(_ => talk.hasSpeaker(req.user.id)))

  def breadcrumb(proposal: Proposal.Full)(implicit req: UserReq[AnyContent]): Breadcrumb =
    listBreadcrumb(proposal.talk).add(proposal.cfp.name.value -> CfpRoutes.list(proposal.talk.slug))
}
