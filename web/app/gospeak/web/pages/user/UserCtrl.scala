package gospeak.web.pages.user

import cats.data.OptionT
import cats.effect.IO
import com.mohiva.play.silhouette.api.Silhouette
import gospeak.core.domain.UserRequest
import gospeak.core.services.email.EmailSrv
import gospeak.core.services.storage._
import gospeak.libs.scala.Extensions._
import gospeak.libs.scala.domain.Page
import gospeak.web.AppConf
import gospeak.web.auth.domain.CookieEnv
import gospeak.web.domain._
import gospeak.web.emails.Emails
import gospeak.web.pages.orga.routes.{GroupCtrl => GroupRoutes}
import gospeak.web.pages.user.UserCtrl._
import gospeak.web.utils.{UICtrl, UserReq}
import play.api.mvc.{Action, AnyContent, ControllerComponents}

import scala.util.control.NonFatal

class UserCtrl(cc: ControllerComponents,
               silhouette: Silhouette[CookieEnv],
               conf: AppConf,
               userRepo: UserUserRepo,
               groupRepo: UserGroupRepo,
               eventRepo: UserEventRepo,
               userRequestRepo: UserUserRequestRepo,
               talkRepo: SpeakerTalkRepo,
               proposalRepo: SpeakerProposalRepo,
               externalProposalRepo: SpeakerExternalProposalRepo,
               cfpRepo: SpeakerCfpRepo,
               emailSrv: EmailSrv) extends UICtrl(cc, silhouette, conf) {
  def index(): Action[AnyContent] = UserAction { implicit req =>
    for {
      incomingEvents <- eventRepo.listIncoming(Page.Params.defaults)
      joinedGroups <- groupRepo.listJoined(Page.Params.defaults)
      currentTalks <- talkRepo.listCurrent(Page.Params.defaults)
      currentProposals <- externalProposalRepo.listCurrentCommon(Page.Params.defaults)
    } yield Ok(html.index(incomingEvents, joinedGroups, currentTalks, currentProposals)(breadcrumb))
  }

  def answerRequest(request: UserRequest.Id): Action[AnyContent] = UserAction { implicit req =>
    userRequestRepo.find(request).flatMap {
      case Some(r: UserRequest.GroupInvite) => (for {
        groupElt <- OptionT(groupRepo.find(r.group))
        orgaElt <- OptionT(userRepo.find(r.createdBy))
        res = Ok(html.answerGroupInvite(r, groupElt, orgaElt)(breadcrumb))
      } yield res).value.map(_.getOrElse(Redirect(routes.UserCtrl.index())))
      case Some(r: UserRequest.TalkInvite) => (for {
        talkElt <- OptionT(talkRepo.find(r.talk))
        speakerElt <- OptionT(userRepo.find(r.createdBy))
        res = Ok(html.answerTalkInvite(r, talkElt, speakerElt)(breadcrumb))
      } yield res).value.map(_.getOrElse(Redirect(routes.UserCtrl.index())))
      case Some(r: UserRequest.ProposalInvite) => (for {
        proposalElt <- OptionT(proposalRepo.find(r.proposal))
        speakerElt <- OptionT(userRepo.find(r.createdBy))
        res = Ok(html.answerProposalInvite(r, proposalElt, speakerElt)(breadcrumb))
      } yield res).value.map(_.getOrElse(Redirect(routes.UserCtrl.index())))
      case Some(r: UserRequest.ExternalProposalInvite) => (for {
        proposalElt <- OptionT(externalProposalRepo.findFull(r.externalProposal))
        speakerElt <- OptionT(userRepo.find(r.createdBy))
        res = Ok(html.answerExternalProposalInvite(r, proposalElt, speakerElt)(breadcrumb))
      } yield res).value.map(_.getOrElse(Redirect(routes.UserCtrl.index())))
      case _ =>
        // TODO: add log here
        IO.pure(Redirect(routes.UserCtrl.index()).flashing("error" -> "Invalid request"))
    }
  }

  def acceptRequest(request: UserRequest.Id): Action[AnyContent] = UserAction { implicit req =>
    userRequestRepo.find(request).map(_.filter(_.isPending(req.now))).flatMap {
      case Some(r: UserRequest.GroupInvite) => (for {
        groupElt <- OptionT(groupRepo.find(r.group))
        speakerElt <- OptionT(userRepo.find(r.createdBy))
        _ <- OptionT.liftF(userRequestRepo.accept(r))
        _ <- OptionT.liftF(emailSrv.send(Emails.inviteOrgaToGroupAccepted(r, groupElt, speakerElt)))
      } yield s"""Invitation to <a href="${GroupRoutes.detail(groupElt.slug)}">${groupElt.name.value}</a> accepted""").value
      case Some(r: UserRequest.TalkInvite) => (for {
        talkElt <- OptionT(talkRepo.find(r.talk))
        speakerElt <- OptionT(userRepo.find(r.createdBy))
        _ <- OptionT.liftF(userRequestRepo.accept(r))
        _ <- OptionT.liftF(emailSrv.send(Emails.inviteSpeakerToTalkAccepted(r, talkElt, speakerElt)))
      } yield s"Invitation to <b>${talkElt.title.value}</b> accepted").value
      case Some(r: UserRequest.ProposalInvite) => (for {
        proposalFull <- OptionT(proposalRepo.findFull(r.proposal))
        speakerElt <- OptionT(userRepo.find(r.createdBy))
        _ <- OptionT.liftF(userRequestRepo.accept(r))
        _ <- OptionT.liftF(emailSrv.send(Emails.inviteSpeakerToProposalAccepted(r, proposalFull, speakerElt)))
      } yield s"Invitation to <b>${proposalFull.title.value}</b> accepted").value
      case Some(r: UserRequest.ExternalProposalInvite) => (for {
        proposalFull <- OptionT(externalProposalRepo.findFull(r.externalProposal))
        speakerElt <- OptionT(userRepo.find(r.createdBy))
        _ <- OptionT.liftF(userRequestRepo.accept(r))
        _ <- OptionT.liftF(emailSrv.send(Emails.inviteSpeakerToExtProposalAccepted(r, proposalFull, speakerElt)))
      } yield s"Invitation to <b>${proposalFull.title.value}</b> accepted").value
      case _ => IO.pure(Some("Request not found or unhandled"))
    }.map(msg => Redirect(routes.UserCtrl.index()).flashing(msg.map("success" -> _).getOrElse("error" -> "Unexpected error :(")))
      .recover { case NonFatal(e) => Redirect(routes.UserCtrl.index()).flashing("error" -> s"Unexpected error: ${e.getMessage}") }
  }

  def rejectRequest(request: UserRequest.Id): Action[AnyContent] = UserAction { implicit req =>
    userRequestRepo.find(request).map(_.filter(_.isPending(req.now))).flatMap {
      case Some(r: UserRequest.GroupInvite) => (for {
        groupElt <- OptionT(groupRepo.find(r.group))
        orgaElt <- OptionT(userRepo.find(r.createdBy))
        _ <- OptionT.liftF(userRequestRepo.reject(r))
        _ <- OptionT.liftF(emailSrv.send(Emails.inviteOrgaToGroupRejected(r, groupElt, orgaElt)))
      } yield s"Invitation to the <b>${groupElt.name.value}</b> group rejected").value
      case Some(r: UserRequest.TalkInvite) => (for {
        talkElt <- OptionT(talkRepo.find(r.talk))
        speakerElt <- OptionT(userRepo.find(r.createdBy))
        _ <- OptionT.liftF(userRequestRepo.reject(r))
        _ <- OptionT.liftF(emailSrv.send(Emails.inviteSpeakerToTalkRejected(r, talkElt, speakerElt)))
      } yield s"Invitation to <b>${talkElt.title.value}</b> rejected").value
      case Some(r: UserRequest.ProposalInvite) => (for {
        proposalFull <- OptionT(proposalRepo.findFull(r.proposal))
        speakerElt <- OptionT(userRepo.find(r.createdBy))
        _ <- OptionT.liftF(userRequestRepo.reject(r))
        _ <- OptionT.liftF(emailSrv.send(Emails.inviteSpeakerToProposalRejected(r, proposalFull, speakerElt)))
      } yield s"Invitation to <b>${proposalFull.title.value}</b> rejected").value
      case Some(r: UserRequest.ExternalProposalInvite) => (for {
        proposalFull <- OptionT(externalProposalRepo.findFull(r.externalProposal))
        speakerElt <- OptionT(userRepo.find(r.createdBy))
        _ <- OptionT.liftF(userRequestRepo.reject(r))
        _ <- OptionT.liftF(emailSrv.send(Emails.inviteSpeakerToExtProposalRejected(r, proposalFull, speakerElt)))
      } yield s"Invitation to <b>${proposalFull.title.value}</b> rejected").value
      case _ => IO.pure(Some("Request not found or unhandled"))
    }.map(msg => Redirect(routes.UserCtrl.index()).flashing(msg.map("success" -> _).getOrElse("error" -> "Unexpected error :(")))
      .recover { case NonFatal(e) => Redirect(routes.UserCtrl.index()).flashing("error" -> s"Unexpected error: ${e.getMessage}") }
  }
}

object UserCtrl {
  def breadcrumb(implicit req: UserReq[AnyContent]): Breadcrumb =
    Breadcrumb(req.user.name.value, routes.UserCtrl.index())

  def groupBreadcrumb(implicit req: UserReq[AnyContent]): Breadcrumb =
    UserCtrl.breadcrumb.add("Groups" -> routes.UserCtrl.index())
}
