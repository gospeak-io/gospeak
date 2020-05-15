package gospeak.web.pages.user.talks.proposals

import cats.data.OptionT
import cats.effect.IO
import com.mohiva.play.silhouette.api.Silhouette
import gospeak.core.domain._
import gospeak.core.domain.messages.Message
import gospeak.core.domain.utils.UserCtx
import gospeak.core.services.email.EmailSrv
import gospeak.core.services.storage._
import gospeak.libs.scala.Extensions._
import gospeak.libs.scala.MessageBus
import gospeak.libs.scala.domain.{Page, Url}
import gospeak.web.AppConf
import gospeak.web.auth.domain.CookieEnv
import gospeak.web.domain.Breadcrumb
import gospeak.web.emails.Emails
import gospeak.web.pages.user.routes.{UserCtrl => UserRoutes}
import gospeak.web.pages.user.talks.TalkCtrl
import gospeak.web.pages.user.talks.cfps.CfpCtrl
import gospeak.web.pages.user.talks.cfps.routes.{CfpCtrl => CfpRoutes}
import gospeak.web.pages.user.talks.proposals.ProposalCtrl._
import gospeak.web.pages.user.talks.routes.{TalkCtrl => TalkRoutes}
import gospeak.web.services.MessageSrv
import gospeak.web.utils.Extensions._
import gospeak.web.utils.{GsForms, UICtrl, UserReq}
import play.api.data.Form
import play.api.mvc._

import scala.util.control.NonFatal

class ProposalCtrl(cc: ControllerComponents,
                   silhouette: Silhouette[CookieEnv],
                   conf: AppConf,
                   userRepo: SpeakerUserRepo,
                   userRequestRepo: SpeakerUserRequestRepo,
                   groupRepo: SpeakerGroupRepo,
                   cfpRepo: SpeakerCfpRepo,
                   eventRepo: SpeakerEventRepo,
                   talkRepo: SpeakerTalkRepo,
                   proposalRepo: SpeakerProposalRepo,
                   externalProposalRepo: SpeakerExternalProposalRepo,
                   externalEventRepo: SpeakerExternalEventRepo,
                   commentRepo: SpeakerCommentRepo,
                   emailSrv: EmailSrv,
                   ms: MessageSrv,
                   bus: MessageBus[Message]) extends UICtrl(cc, silhouette, conf) {
  def list(talk: Talk.Slug, params: Page.Params): Action[AnyContent] = UserAction { implicit req =>
    (for {
      talkElt <- OptionT(talkRepo.find(talk))
      proposals <- OptionT.liftF(externalProposalRepo.listCommon(talkElt.id, params))
      b = listBreadcrumb(talkElt)
    } yield Ok(html.list(talkElt, proposals)(b))).value.map(_.getOrElse(talkNotFound(talk)))
  }

  def detailExt(talk: Talk.Slug, proposal: ExternalProposal.Id): Action[AnyContent] = UserAction { implicit req =>
    (for {
      proposalElt <- OptionT(externalProposalRepo.findFull(proposal))
      invites <- OptionT.liftF(userRequestRepo.listPendingInvites(proposalElt.id))
      speakers <- OptionT.liftF(userRepo.list(proposalElt.users))
      b = breadcrumb(proposalElt.talk, proposalElt.event, proposalElt.proposal)
    } yield Ok(html.detailExt(proposalElt, invites, speakers)(b))).value.map(_.getOrElse(extProposalNotFound(talk, proposal)))
  }

  def editExt(talk: Talk.Slug, proposal: ExternalProposal.Id, redirect: Option[String]): Action[AnyContent] = UserAction { implicit req =>
    editExtView(talk, proposal, GsForms.externalProposal, redirect)
  }

  def doEditExt(talk: Talk.Slug, proposal: ExternalProposal.Id, redirect: Option[String]): Action[AnyContent] = UserAction { implicit req =>
    GsForms.externalProposal.bindFromRequest.fold(
      formWithErrors => editExtView(talk, proposal, formWithErrors, redirect),
      data => externalProposalRepo.edit(proposal)(data).map(_ => redirectOr(redirect, routes.ProposalCtrl.detailExt(talk, proposal)))
    )
  }

  private def editExtView(talk: Talk.Slug, proposal: ExternalProposal.Id, form: Form[ExternalProposal.Data], redirect: Option[String])(implicit req: UserReq[AnyContent], ctx: UserCtx): IO[Result] = {
    (for {
      talkElt <- OptionT(talkRepo.find(talk))
      proposalElt <- OptionT(externalProposalRepo.find(proposal))
      eventElt <- OptionT(externalEventRepo.find(proposalElt.event))
      filledForm = if (form.hasErrors) form else form.fill(proposalElt.data)
      b = breadcrumb(talkElt, eventElt, proposalElt).add("Edit" -> routes.ProposalCtrl.editExt(talk, proposal))
    } yield Ok(html.editExt(talkElt, proposalElt, eventElt, filledForm, redirect)(b))).value.map(_.getOrElse(extProposalNotFound(talk, proposal)))
  }

  def inviteSpeakerExt(talk: Talk.Slug, proposal: ExternalProposal.Id): Action[AnyContent] = UserAction { implicit req =>
    val next = Redirect(routes.ProposalCtrl.detailExt(talk, proposal))
    GsForms.invite.bindFromRequest.fold(
      formWithErrors => IO.pure(next.flashing(formWithErrors.flash)),
      data => (for {
        proposalElt <- OptionT(externalProposalRepo.findFull(proposal)).filter(_.talk.slug == talk)
        invite <- OptionT.liftF(userRequestRepo.invite(proposalElt.id, data.email))
        _ <- OptionT.liftF(emailSrv.send(Emails.inviteSpeakerToExtProposal(invite, proposalElt, data.message)))
      } yield next.flashing("success" -> s"<b>${invite.email.value}</b> is invited as speaker")).value.map(_.getOrElse(extProposalNotFound(talk, proposal)))
    )
  }

  def cancelInviteSpeakerExt(talk: Talk.Slug, proposal: ExternalProposal.Id, request: UserRequest.Id): Action[AnyContent] = UserAction { implicit req =>
    (for {
      proposalElt <- OptionT(externalProposalRepo.findFull(proposal)).filter(_.talk.slug == talk)
      invite <- OptionT.liftF(userRequestRepo.cancelExternalProposalInvite(request))
      _ <- OptionT.liftF(emailSrv.send(Emails.inviteSpeakerToExtProposalCanceled(invite, proposalElt)))
      next = Redirect(routes.ProposalCtrl.detailExt(talk, proposal)).flashing("success" -> s"Invitation to <b>${invite.email.value}</b> has been canceled")
    } yield next).value.map(_.getOrElse(extProposalNotFound(talk, proposal)))
  }

  def removeSpeakerExt(talk: Talk.Slug, proposal: ExternalProposal.Id, speaker: User.Slug): Action[AnyContent] = UserAction { implicit req =>
    val next = Redirect(routes.ProposalCtrl.detailExt(talk, proposal))
    (for {
      proposalElt <- OptionT(externalProposalRepo.findFull(proposal)).filter(_.talk.slug == talk)
      speakerElt <- OptionT(userRepo.find(speaker))
      res <- OptionT.liftF {
        externalProposalRepo.removeSpeaker(proposal, speakerElt.id).flatMap { _ =>
          if (speakerElt.id == req.user.id) IO.pure(Redirect(UserRoutes.index()).flashing("success" -> s"You removed yourself from <b>$talk</b> proposal"))
          else emailSrv.send(Emails.speakerRemovedFromExtProposal(proposalElt, speakerElt))
            .map(_ => next.flashing("success" -> s"<b>${speakerElt.name.value}</b> removed from speakers"))
        }.recover { case NonFatal(e) => next.flashing("error" -> s"<b>${speakerElt.name.value}</b> not removed: ${e.getMessage}") }
      }
    } yield res).value.map(_.getOrElse(extProposalNotFound(talk, proposal)))
  }

  def doAddSlidesExt(talk: Talk.Slug, proposal: ExternalProposal.Id): Action[AnyContent] = UserAction { implicit req =>
    val next = Redirect(routes.ProposalCtrl.detailExt(talk, proposal))
    GsForms.embed.bindFromRequest.fold(
      formWithErrors => IO.pure(next.flashing(formWithErrors.flash)),
      data => Url.Slides.from(data.value) match {
        case Left(err) => IO.pure(next.flashing("error" -> err.getMessage))
        case Right(slides) => externalProposalRepo.editSlides(proposal, slides).map(_ => next)
      }
    )
  }

  def doAddVideoExt(talk: Talk.Slug, proposal: ExternalProposal.Id): Action[AnyContent] = UserAction { implicit req =>
    val next = Redirect(routes.ProposalCtrl.detailExt(talk, proposal))
    GsForms.embed.bindFromRequest.fold(formWithErrors => IO.pure(next.flashing(formWithErrors.flash)), {
      case video: Url.Video => externalProposalRepo.editVideo(proposal, video).map(_ => next)
      case data => IO.pure(next.flashing("error" -> s"${data.value} is not a valid video url"))
    })
  }

  def doRemoveExt(talk: Talk.Slug, proposal: ExternalProposal.Id): Action[AnyContent] = UserAction { implicit req =>
    externalProposalRepo.remove(proposal).map(_ => Redirect(TalkRoutes.detail(talk)))
  }

  def create(talk: Talk.Slug, cfp: Cfp.Slug): Action[AnyContent] = UserAction { implicit req =>
    createForm(GsForms.proposal, talk, cfp)
  }

  def doCreate(talk: Talk.Slug, cfp: Cfp.Slug): Action[AnyContent] = UserAction { implicit req =>
    GsForms.proposal.bindFromRequest.fold(
      formWithErrors => createForm(formWithErrors, talk, cfp),
      data => {
        (for {
          talkElt <- OptionT(talkRepo.find(talk))
          cfpElt <- OptionT(cfpRepo.findRead(cfp))
          proposalElt <- OptionT.liftF(proposalRepo.create(talkElt.id, cfpElt.id, data, talkElt.speakers))
          _ <- OptionT(ms.proposalCreated(cfpElt, proposalElt)).map(bus.publish)
          msg = s"Well done! Your proposal <b>${proposalElt.title.value}</b> is proposed to <b>${cfpElt.name.value}</b>"
        } yield Redirect(routes.ProposalCtrl.detail(talk, cfp)).flashing("success" -> msg)).value.map(_.getOrElse(cfpNotFound(talk, cfp)))
      }
    )
  }

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

  def detail(talk: Talk.Slug, cfp: Cfp.Slug): Action[AnyContent] = UserAction { implicit req =>
    proposalView(talk, cfp)
  }

  def doSendComment(talk: Talk.Slug, cfp: Cfp.Slug): Action[AnyContent] = UserAction { implicit req =>
    val next = redirectToPreviousPageOr(routes.ProposalCtrl.detail(talk, cfp))
    GsForms.comment.bindFromRequest.fold(
      formWithErrors => IO.pure(next.flashing(formWithErrors.flash)),
      data => (for {
        proposalElt <- OptionT(proposalRepo.find(talk, cfp))
        cfpElt <- OptionT(cfpRepo.find(proposalElt.cfp))
        groupElt <- OptionT(groupRepo.find(cfpElt.group))
        orgas <- OptionT.liftF(userRepo.list(groupElt.owners.toList))
        comment <- OptionT.liftF(commentRepo.addComment(proposalElt.id, data))
        _ <- OptionT.liftF(orgas.toNel.map(o => emailSrv.send(Emails.proposalCommentAddedForOrga(groupElt, cfpElt, proposalElt, o, comment))).sequence)
      } yield next).value.map(_.getOrElse(proposalNotFound(talk, cfp)))
    )
  }

  private def proposalView(talk: Talk.Slug, cfp: Cfp.Slug)(implicit req: UserReq[AnyContent], ctx: UserCtx): IO[Result] = {
    (for {
      proposalFull <- OptionT(proposalRepo.findFull(talk, cfp))
      invites <- OptionT.liftF(userRequestRepo.listPendingInvites(proposalFull.id))
      speakers <- OptionT.liftF(userRepo.list(proposalFull.users))
      comments <- OptionT.liftF(commentRepo.getComments(proposalFull.id))
      b = breadcrumb(proposalFull)
      res = Ok(html.detail(proposalFull, speakers, comments, invites)(b))
    } yield res).value.map(_.getOrElse(proposalNotFound(talk, cfp)))
  }

  def edit(talk: Talk.Slug, cfp: Cfp.Slug, redirect: Option[String]): Action[AnyContent] = UserAction { implicit req =>
    editView(talk, cfp, GsForms.proposal, redirect)
  }

  def doEdit(talk: Talk.Slug, cfp: Cfp.Slug, redirect: Option[String]): Action[AnyContent] = UserAction { implicit req =>
    GsForms.proposal.bindFromRequest.fold(
      formWithErrors => editView(talk, cfp, formWithErrors, redirect),
      data => proposalRepo.edit(talk, cfp, data).map(_ => redirectOr(redirect, routes.ProposalCtrl.detail(talk, cfp)))
    )
  }

  private def editView(talk: Talk.Slug, cfp: Cfp.Slug, form: Form[Proposal.Data], redirect: Option[String])(implicit req: UserReq[AnyContent], ctx: UserCtx): IO[Result] = {
    (for {
      proposalFull <- OptionT(proposalRepo.findFull(talk, cfp))
      filledForm = if (form.hasErrors) form else form.fill(proposalFull.data)
      b = breadcrumb(proposalFull).add("Edit" -> routes.ProposalCtrl.edit(talk, cfp))
    } yield Ok(html.edit(filledForm, proposalFull, redirect)(b))).value.map(_.getOrElse(talkNotFound(talk)))
  }

  def inviteSpeaker(talk: Talk.Slug, cfp: Cfp.Slug): Action[AnyContent] = UserAction { implicit req =>
    val next = Redirect(routes.ProposalCtrl.detail(talk, cfp))
    GsForms.invite.bindFromRequest.fold(
      formWithErrors => IO.pure(next.flashing(formWithErrors.flash)),
      data => (for {
        cfpElt <- OptionT(cfpRepo.findRead(cfp))
        proposalElt <- OptionT(proposalRepo.find(talk, cfp))
        eventElt <- proposalElt.event.map(id => OptionT(eventRepo.find(id))).sequence
        invite <- OptionT.liftF(userRequestRepo.invite(proposalElt.id, data.email))
        _ <- OptionT.liftF(emailSrv.send(Emails.inviteSpeakerToProposal(invite, cfpElt, eventElt, proposalElt, data.message)))
      } yield next.flashing("success" -> s"<b>${invite.email.value}</b> is invited as speaker")).value.map(_.getOrElse(proposalNotFound(talk, cfp)))
    )
  }

  def cancelInviteSpeaker(talk: Talk.Slug, cfp: Cfp.Slug, request: UserRequest.Id): Action[AnyContent] = UserAction { implicit req =>
    (for {
      proposalElt <- OptionT(proposalRepo.find(talk, cfp))
      invite <- OptionT.liftF(userRequestRepo.cancelProposalInvite(request))
      _ <- OptionT.liftF(emailSrv.send(Emails.inviteSpeakerToProposalCanceled(invite, proposalElt)))
      next = Redirect(routes.ProposalCtrl.detail(talk, cfp)).flashing("success" -> s"Invitation to <b>${invite.email.value}</b> has been canceled")
    } yield next).value.map(_.getOrElse(proposalNotFound(talk, cfp)))
  }

  def removeSpeaker(talk: Talk.Slug, cfp: Cfp.Slug, speaker: User.Slug): Action[AnyContent] = UserAction { implicit req =>
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
  }

  def doAddSlides(talk: Talk.Slug, cfp: Cfp.Slug): Action[AnyContent] = UserAction { implicit req =>
    val next = Redirect(routes.ProposalCtrl.detail(talk, cfp))
    GsForms.embed.bindFromRequest.fold(
      formWithErrors => IO.pure(next.flashing(formWithErrors.flash)),
      data => Url.Slides.from(data.value) match {
        case Left(err) => IO.pure(next.flashing("error" -> err.getMessage))
        case Right(slides) => proposalRepo.editSlides(talk, cfp, slides).map(_ => next)
      }
    )
  }

  def doAddVideo(talk: Talk.Slug, cfp: Cfp.Slug): Action[AnyContent] = UserAction { implicit req =>
    val next = Redirect(routes.ProposalCtrl.detail(talk, cfp))
    GsForms.embed.bindFromRequest.fold(formWithErrors => IO.pure(next.flashing(formWithErrors.flash)), {
      case video: Url.Video => proposalRepo.editVideo(talk, cfp, video).map(_ => next)
      case data => IO.pure(next.flashing("error" -> s"${data.value} is not a valid video url"))
    })
  }
}

object ProposalCtrl {
  def listBreadcrumb(talk: Talk)(implicit req: UserReq[AnyContent]): Breadcrumb =
    TalkCtrl.breadcrumb(talk).add("Proposals", routes.ProposalCtrl.list(talk.slug), talk.hasSpeaker(req.user.id))

  def breadcrumb(proposal: Proposal.Full)(implicit req: UserReq[AnyContent]): Breadcrumb =
    listBreadcrumb(proposal.talk).add(proposal.cfp.name.value -> CfpRoutes.list(proposal.talk.slug))

  def breadcrumb(talk: Talk, event: ExternalEvent, proposal: ExternalProposal)(implicit req: UserReq[AnyContent]): Breadcrumb =
    listBreadcrumb(talk).add(event.name.value -> routes.ProposalCtrl.detailExt(talk.slug, proposal.id))
}
