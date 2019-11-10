package fr.gospeak.web.pages.user

import cats.data.OptionT
import cats.effect.IO
import com.mohiva.play.silhouette.api.Silhouette
import fr.gospeak.core.ApplicationConf
import fr.gospeak.core.domain.{User, UserRequest}
import fr.gospeak.core.services.storage._
import fr.gospeak.infra.services.EmailSrv
import fr.gospeak.libs.scalautils.Extensions._
import fr.gospeak.libs.scalautils.domain.Page
import fr.gospeak.web.auth.domain.CookieEnv
import fr.gospeak.web.domain._
import fr.gospeak.web.emails.Emails
import fr.gospeak.web.pages.orga.routes.{GroupCtrl => GroupRoutes}
import fr.gospeak.web.pages.user.UserCtrl._
import fr.gospeak.web.utils.UICtrl
import play.api.mvc.{Action, AnyContent, ControllerComponents}

import scala.util.control.NonFatal

class UserCtrl(cc: ControllerComponents,
               silhouette: Silhouette[CookieEnv],
               env: ApplicationConf.Env,
               userRepo: UserUserRepo,
               groupRepo: UserGroupRepo,
               eventRepo: UserEventRepo,
               userRequestRepo: UserUserRequestRepo,
               talkRepo: SpeakerTalkRepo,
               proposalRepo: SpeakerProposalRepo,
               cfpRepo: SpeakerCfpRepo,
               emailSrv: EmailSrv) extends UICtrl(cc, silhouette, env) {
  def index(): Action[AnyContent] = SecuredActionIO { implicit req =>
    for {
      incomingEvents <- eventRepo.listIncoming(Page.Params.defaults)(req.user.id, req.now)
      joinedGroups <- groupRepo.listJoined(req.user.id, Page.Params.defaults)
      talks <- talkRepo.list(req.user.id, Page.Params.defaults)
      proposals <- proposalRepo.listFull(req.user.id, Page.Params.defaults)
      b = breadcrumb(req.user)
    } yield Ok(html.index(incomingEvents, joinedGroups, talks, proposals)(b))
  }

  def answerRequest(request: UserRequest.Id): Action[AnyContent] = SecuredActionIO { implicit req =>
    userRequestRepo.find(request).flatMap {
      case Some(r: UserRequest.GroupInvite) => (for {
        groupElt <- OptionT(groupRepo.find(r.group))
        orgaElt <- OptionT(userRepo.find(r.createdBy))
        res = Ok(html.answerGroupInvite(r, groupElt, orgaElt)(breadcrumb(req.user)))
      } yield res).value.map(_.getOrElse(Redirect(routes.UserCtrl.index())))
      case Some(r: UserRequest.TalkInvite) => (for {
        talkElt <- OptionT(talkRepo.find(r.talk))
        speakerElt <- OptionT(userRepo.find(r.createdBy))
        res = Ok(html.answerTalkInvite(r, talkElt, speakerElt)(breadcrumb(req.user)))
      } yield res).value.map(_.getOrElse(Redirect(routes.UserCtrl.index())))
      case Some(r: UserRequest.ProposalInvite) => (for {
        proposalElt <- OptionT(proposalRepo.find(r.proposal))
        speakerElt <- OptionT(userRepo.find(r.createdBy))
        res = Ok(html.answerProposalInvite(r, proposalElt, speakerElt)(breadcrumb(req.user)))
      } yield res).value.map(_.getOrElse(Redirect(routes.UserCtrl.index())))
      case _ =>
        // TODO: add log here
        IO.pure(Redirect(routes.UserCtrl.index()).flashing("error" -> "Invalid request"))
    }
  }

  def acceptRequest(request: UserRequest.Id): Action[AnyContent] = SecuredActionIO { implicit req =>
    userRequestRepo.find(request).map(_.filter(_.isPending(req.now))).flatMap {
      case Some(r: UserRequest.GroupInvite) => (for {
        groupElt <- OptionT(groupRepo.find(r.group))
        speakerElt <- OptionT(userRepo.find(r.createdBy))
        _ <- OptionT.liftF(userRequestRepo.accept(r, req.user.id, req.now))
        _ <- OptionT.liftF(emailSrv.send(Emails.inviteOrgaToGroupAccepted(r, groupElt, speakerElt)))
      } yield s"""Invitation to <a href="${GroupRoutes.detail(groupElt.slug)}">${groupElt.name.value}</a> accepted""").value
      case Some(r: UserRequest.TalkInvite) => (for {
        talkElt <- OptionT(talkRepo.find(r.talk))
        speakerElt <- OptionT(userRepo.find(r.createdBy))
        _ <- OptionT.liftF(userRequestRepo.accept(r, req.user.id, req.now))
        _ <- OptionT.liftF(emailSrv.send(Emails.inviteSpeakerToTalkAccepted(r, talkElt, speakerElt)))
      } yield s"Invitation to <b>${talkElt.title.value}</b> accepted").value
      case Some(r: UserRequest.ProposalInvite) => (for {
        proposalFull <- OptionT(proposalRepo.findFull(r.proposal))
        speakerElt <- OptionT(userRepo.find(r.createdBy))
        _ <- OptionT.liftF(userRequestRepo.accept(r, req.user.id, req.now))
        _ <- OptionT.liftF(emailSrv.send(Emails.inviteSpeakerToProposalAccepted(r, proposalFull, speakerElt)))
      } yield s"Invitation to <b>${proposalFull.title.value}</b> accepted").value
      case _ => IO.pure(Some("Request not found or unhandled"))
    }.map(msg => Redirect(routes.UserCtrl.index()).flashing(msg.map("success" -> _).getOrElse("error" -> "Unexpected error :(")))
      .recover { case NonFatal(e) => Redirect(routes.UserCtrl.index()).flashing("error" -> s"Unexpected error: ${e.getMessage}") }
  }

  def rejectRequest(request: UserRequest.Id): Action[AnyContent] = SecuredActionIO { implicit req =>
    userRequestRepo.find(request).map(_.filter(_.isPending(req.now))).flatMap {
      case Some(r: UserRequest.GroupInvite) => (for {
        groupElt <- OptionT(groupRepo.find(r.group))
        orgaElt <- OptionT(userRepo.find(r.createdBy))
        _ <- OptionT.liftF(userRequestRepo.reject(r, req.user.id, req.now))
        _ <- OptionT.liftF(emailSrv.send(Emails.inviteOrgaToGroupRejected(r, groupElt, orgaElt)))
      } yield s"Invitation to the <b>${groupElt.name.value}</b> group rejected").value
      case Some(r: UserRequest.TalkInvite) => (for {
        talkElt <- OptionT(talkRepo.find(r.talk))
        speakerElt <- OptionT(userRepo.find(r.createdBy))
        _ <- OptionT.liftF(userRequestRepo.reject(r, req.user.id, req.now))
        _ <- OptionT.liftF(emailSrv.send(Emails.inviteSpeakerToTalkRejected(r, talkElt, speakerElt)))
      } yield s"Invitation to <b>${talkElt.title.value}</b> rejected").value
      case Some(r: UserRequest.ProposalInvite) => (for {
        proposalFull <- OptionT(proposalRepo.findFull(r.proposal))
        speakerElt <- OptionT(userRepo.find(r.createdBy))
        _ <- OptionT.liftF(userRequestRepo.reject(r, req.user.id, req.now))
        _ <- OptionT.liftF(emailSrv.send(Emails.inviteSpeakerToProposalRejected(r, proposalFull, speakerElt)))
      } yield s"Invitation to <b>${proposalFull.title.value}</b> rejected").value
      case _ => IO.pure(Some("Request not found or unhandled"))
    }.map(msg => Redirect(routes.UserCtrl.index()).flashing(msg.map("success" -> _).getOrElse("error" -> "Unexpected error :(")))
      .recover { case NonFatal(e) => Redirect(routes.UserCtrl.index()).flashing("error" -> s"Unexpected error: ${e.getMessage}") }
  }
}

object UserCtrl {
  def breadcrumb(user: User): Breadcrumb =
    Breadcrumb(user.name.value -> routes.UserCtrl.index())

  def groupBreadcrumb(user: User): Breadcrumb =
    UserCtrl.breadcrumb(user).add("Groups" -> routes.UserCtrl.index())
}
