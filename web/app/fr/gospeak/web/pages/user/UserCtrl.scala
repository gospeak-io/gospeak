package fr.gospeak.web.pages.user

import java.time.Instant

import cats.data.OptionT
import cats.effect.IO
import com.mohiva.play.silhouette.api.Silhouette
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
import play.api.mvc._

import scala.util.control.NonFatal

class UserCtrl(cc: ControllerComponents,
               silhouette: Silhouette[CookieEnv],
               userRepo: UserUserRepo,
               groupRepo: UserGroupRepo,
               eventRepo: UserEventRepo,
               userRequestRepo: UserUserRequestRepo,
               talkRepo: SpeakerTalkRepo,
               proposalRepo: SpeakerProposalRepo,
               cfpRepo: SpeakerCfpRepo,
               emailSrv: EmailSrv) extends UICtrl(cc, silhouette) {

  import silhouette._

  def index(): Action[AnyContent] = SecuredAction.async { implicit req =>
    val now = Instant.now()
    (for {
      incomingEvents <- eventRepo.listIncoming(Page.Params.defaults)(user, now)
      joinedGroups <- groupRepo.listJoined(user, Page.Params.defaults)
      talks <- talkRepo.list(user, Page.Params.defaults)
      proposals <- proposalRepo.listFull(user, Page.Params.defaults)
      b = breadcrumb(req.identity.user)
    } yield Ok(html.index(incomingEvents, joinedGroups, talks, proposals)(b))).unsafeToFuture()
  }

  def answerRequest(request: UserRequest.Id): Action[AnyContent] = SecuredAction.async { implicit req =>
    val now = Instant.now()
    userRequestRepo.find(request).flatMap {
      case Some(r: UserRequest.GroupInvite) => (for {
        groupElt <- OptionT(groupRepo.find(r.group))
        orgaElt <- OptionT(userRepo.find(r.createdBy))
        res = Ok(html.answerGroupInvite(r, groupElt, orgaElt, now)(breadcrumb(req.identity.user)))
      } yield res).value.map(_.getOrElse(Redirect(routes.UserCtrl.index())))
      case Some(r: UserRequest.TalkInvite) => (for {
        talkElt <- OptionT(talkRepo.find(r.talk))
        speakerElt <- OptionT(userRepo.find(r.createdBy))
        res = Ok(html.answerTalkInvite(r, talkElt, speakerElt, now)(breadcrumb(req.identity.user)))
      } yield res).value.map(_.getOrElse(Redirect(routes.UserCtrl.index())))
      case Some(r: UserRequest.ProposalInvite) => (for {
        proposalElt <- OptionT(proposalRepo.find(r.proposal))
        speakerElt <- OptionT(userRepo.find(r.createdBy))
        res = Ok(html.answerProposalInvite(r, proposalElt, speakerElt, now)(breadcrumb(req.identity.user)))
      } yield res).value.map(_.getOrElse(Redirect(routes.UserCtrl.index())))
      case _ =>
        // TODO: add log here
        IO.pure(Redirect(routes.UserCtrl.index()).flashing("error" -> "Invalid request"))
    }.unsafeToFuture()
  }

  def acceptRequest(request: UserRequest.Id): Action[AnyContent] = SecuredAction.async { implicit req =>
    val now = Instant.now()
    userRequestRepo.find(request).map(_.filter(_.isPending(now))).flatMap {
      case Some(r: UserRequest.GroupInvite) => (for {
        groupElt <- OptionT(groupRepo.find(r.group))
        speakerElt <- OptionT(userRepo.find(r.createdBy))
        _ <- OptionT.liftF(userRequestRepo.accept(r, user, now))
        _ <- OptionT.liftF(emailSrv.send(Emails.inviteOrgaToGroupAccepted(r, groupElt, speakerElt, req.identity.user)))
      } yield s"""Invitation to <a href="${GroupRoutes.detail(groupElt.slug)}">${groupElt.name.value}</a> accepted""").value
      case Some(r: UserRequest.TalkInvite) => (for {
        talkElt <- OptionT(talkRepo.find(r.talk))
        speakerElt <- OptionT(userRepo.find(r.createdBy))
        _ <- OptionT.liftF(userRequestRepo.accept(r, user, now))
        _ <- OptionT.liftF(emailSrv.send(Emails.inviteSpeakerToTalkAccepted(r, talkElt, speakerElt, req.identity.user)))
      } yield s"Invitation to <b>${talkElt.title.value}</b> accepted").value
      case Some(r: UserRequest.ProposalInvite) => (for {
        proposalFull <- OptionT(proposalRepo.findFull(r.proposal))
        speakerElt <- OptionT(userRepo.find(r.createdBy))
        _ <- OptionT.liftF(userRequestRepo.accept(r, user, now))
        _ <- OptionT.liftF(emailSrv.send(Emails.inviteSpeakerToProposalAccepted(r, proposalFull, speakerElt, req.identity.user)))
      } yield s"Invitation to <b>${proposalFull.title.value}</b> accepted").value
      case _ => IO.pure(Some("Request not found or unhandled"))
    }.map(msg => Redirect(routes.UserCtrl.index()).flashing(msg.map("success" -> _).getOrElse("error" -> "Unexpected error :(")))
      .recover { case NonFatal(e) => Redirect(routes.UserCtrl.index()).flashing("error" -> s"Unexpected error: ${e.getMessage}") }
      .unsafeToFuture()
  }

  def rejectRequest(request: UserRequest.Id): Action[AnyContent] = SecuredAction.async { implicit req =>
    val now = Instant.now()
    userRequestRepo.find(request).map(_.filter(_.isPending(now))).flatMap {
      case Some(r: UserRequest.GroupInvite) => (for {
        groupElt <- OptionT(groupRepo.find(r.group))
        orgaElt <- OptionT(userRepo.find(r.createdBy))
        _ <- OptionT.liftF(userRequestRepo.reject(r, user, now))
        _ <- OptionT.liftF(emailSrv.send(Emails.inviteOrgaToGroupRejected(r, groupElt, orgaElt, req.identity.user)))
      } yield s"Invitation to the <b>${groupElt.name.value}</b> group rejected").value
      case Some(r: UserRequest.TalkInvite) => (for {
        talkElt <- OptionT(talkRepo.find(r.talk))
        speakerElt <- OptionT(userRepo.find(r.createdBy))
        _ <- OptionT.liftF(userRequestRepo.reject(r, user, now))
        _ <- OptionT.liftF(emailSrv.send(Emails.inviteSpeakerToTalkRejected(r, talkElt, speakerElt, req.identity.user)))
      } yield s"Invitation to <b>${talkElt.title.value}</b> rejected").value
      case Some(r: UserRequest.ProposalInvite) => (for {
        proposalFull <- OptionT(proposalRepo.findFull(r.proposal))
        speakerElt <- OptionT(userRepo.find(r.createdBy))
        _ <- OptionT.liftF(userRequestRepo.reject(r, user, now))
        _ <- OptionT.liftF(emailSrv.send(Emails.inviteSpeakerToProposalRejected(r, proposalFull, speakerElt, req.identity.user)))
      } yield s"Invitation to <b>${proposalFull.title.value}</b> rejected").value
      case _ => IO.pure(Some("Request not found or unhandled"))
    }.map(msg => Redirect(routes.UserCtrl.index()).flashing(msg.map("success" -> _).getOrElse("error" -> "Unexpected error :(")))
      .recover { case NonFatal(e) => Redirect(routes.UserCtrl.index()).flashing("error" -> s"Unexpected error: ${e.getMessage}") }
      .unsafeToFuture()
  }
}

object UserCtrl {
  def breadcrumb(user: User): Breadcrumb =
    Breadcrumb(user.name.value -> routes.UserCtrl.index())

  def groupBreadcrumb(user: User): Breadcrumb =
    UserCtrl.breadcrumb(user).add("Groups" -> routes.UserCtrl.index())
}
