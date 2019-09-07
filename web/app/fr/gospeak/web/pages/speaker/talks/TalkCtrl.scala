package fr.gospeak.web.pages.speaker.talks

import java.time.Instant

import cats.data.OptionT
import cats.effect.IO
import com.mohiva.play.silhouette.api.Silhouette
import com.mohiva.play.silhouette.api.actions.SecuredRequest
import fr.gospeak.core.domain.{Talk, User, UserRequest}
import fr.gospeak.core.services.storage._
import fr.gospeak.infra.services.EmailSrv
import fr.gospeak.libs.scalautils.Extensions._
import fr.gospeak.libs.scalautils.domain.{Page, Slides, Video}
import fr.gospeak.web.auth.domain.CookieEnv
import fr.gospeak.web.auth.emails.Emails
import fr.gospeak.web.domain.Breadcrumb
import fr.gospeak.web.pages.speaker.talks.TalkCtrl._
import fr.gospeak.web.pages.user.UserCtrl
import fr.gospeak.web.pages.user.routes.{UserCtrl => UserRoutes}
import fr.gospeak.web.utils.{GenericForm, UICtrl}
import play.api.data.Form
import play.api.mvc._

import scala.util.control.NonFatal

class TalkCtrl(cc: ControllerComponents,
               silhouette: Silhouette[CookieEnv],
               userRepo: SpeakerUserRepo,
               userRequestRepo: SpeakerUserRequestRepo,
               eventRepo: SpeakerEventRepo,
               talkRepo: SpeakerTalkRepo,
               proposalRepo: SpeakerProposalRepo,
               emailSrv: EmailSrv) extends UICtrl(cc, silhouette) {

  import silhouette._

  def list(params: Page.Params): Action[AnyContent] = SecuredAction.async { implicit req =>
    (for {
      talks <- talkRepo.list(user, params)
      b = listBreadcrumb(req.identity.user)
    } yield Ok(html.list(talks)(b))).unsafeToFuture()
  }

  def create(): Action[AnyContent] = SecuredAction.async { implicit req =>
    createForm(TalkForms.create).unsafeToFuture()
  }

  def doCreate(): Action[AnyContent] = SecuredAction.async { implicit req =>
    val now = Instant.now()
    TalkForms.create.bindFromRequest.fold(
      formWithErrors => createForm(formWithErrors),
      data => talkRepo.find(user, data.slug).flatMap {
        case Some(duplicate) =>
          createForm(TalkForms.create.fillAndValidate(data).withError("slug", s"Slug already taken by talk: ${duplicate.title.value}"))
        case None =>
          talkRepo.create(data, by, now).map { _ => Redirect(routes.TalkCtrl.detail(data.slug)) }
      }
    ).unsafeToFuture()
  }

  private def createForm(form: Form[Talk.Data])(implicit req: SecuredRequest[CookieEnv, AnyContent]): IO[Result] = {
    val b = listBreadcrumb(req.identity.user).add("New" -> routes.TalkCtrl.create())
    IO.pure(Ok(html.create(form)(b)))
  }

  def detail(talk: Talk.Slug): Action[AnyContent] = SecuredAction.async { implicit req =>
    (for {
      talkElt <- OptionT(talkRepo.find(user, talk))
      invites <- OptionT.liftF(userRequestRepo.listPendingTalkInvites(talkElt.id))
      speakers <- OptionT.liftF(userRepo.list(talkElt.users))
      proposals <- OptionT.liftF(proposalRepo.list(talkElt.id, Page.Params.defaults))
      events <- OptionT.liftF(eventRepo.list(proposals.items.flatMap(_._2.event)))
      b = breadcrumb(req.identity.user, talkElt)
    } yield Ok(html.detail(talkElt, speakers, invites, proposals, events, TalkForms.addSpeaker, GenericForm.embed)(b))).value.map(_.getOrElse(talkNotFound(talk))).unsafeToFuture()
  }

  def edit(talk: Talk.Slug): Action[AnyContent] = SecuredAction.async { implicit req =>
    editForm(talk, TalkForms.create).unsafeToFuture()
  }

  def doEdit(talk: Talk.Slug): Action[AnyContent] = SecuredAction.async { implicit req =>
    val now = Instant.now()
    TalkForms.create.bindFromRequest.fold(
      formWithErrors => editForm(talk, formWithErrors),
      data => talkRepo.find(user, data.slug).flatMap {
        case Some(duplicate) if data.slug != talk =>
          editForm(talk, TalkForms.create.fillAndValidate(data).withError("slug", s"Slug already taken by talk: ${duplicate.title.value}"))
        case _ =>
          talkRepo.edit(user, talk)(data, now).map { _ => Redirect(routes.TalkCtrl.detail(data.slug)) }
      }
    ).unsafeToFuture()
  }

  private def editForm(talk: Talk.Slug, form: Form[Talk.Data])(implicit req: SecuredRequest[CookieEnv, AnyContent]): IO[Result] = {
    talkRepo.find(user, talk).map {
      case Some(talkElt) =>
        val b = breadcrumb(req.identity.user, talkElt).add("Edit" -> routes.TalkCtrl.edit(talk))
        val filledForm = if (form.hasErrors) form else form.fill(talkElt.data)
        Ok(html.edit(talkElt, filledForm)(b))
      case None =>
        talkNotFound(talk)
    }
  }

  def inviteSpeaker(talk: Talk.Slug): Action[AnyContent] = SecuredAction.async { implicit req =>
    val now = Instant.now()
    val next = Redirect(routes.TalkCtrl.detail(talk))
    TalkForms.addSpeaker.bindFromRequest.fold(
      formWithErrors => IO.pure(next.flashing(formWithErrors.errors.map(e => "error" -> e.format): _*)),
      email => (for {
        talkElt <- OptionT(talkRepo.find(user, talk))
        invite <- OptionT.liftF(userRequestRepo.invite(talkElt.id, email, user, now))
        _ <- OptionT.liftF(emailSrv.send(Emails.inviteSpeakerToTalk(invite, talkElt, req.identity.user)))
      } yield next.flashing("success" -> s"<b>$email</b> is invited as speaker")).value.map(_.getOrElse(talkNotFound(talk)))
    ).unsafeToFuture()
  }

  def cancelInviteSpeaker(talk: Talk.Slug, request: UserRequest.Id): Action[AnyContent] = SecuredAction.async { implicit req =>
    val now = Instant.now()
    (for {
      talkElt <- OptionT(talkRepo.find(user, talk))
      invite <- OptionT.liftF(userRequestRepo.cancelInvite(request, user, now))
      _ <- OptionT.liftF(emailSrv.send(Emails.inviteSpeakerToTalkCanceled(invite, talkElt, req.identity.user)))
      next = Redirect(routes.TalkCtrl.detail(talkElt.slug)).flashing("success" -> s"Invitation to <b>${invite.email.value}</b> has been canceled")
    } yield next).value.map(_.getOrElse(talkNotFound(talk))).unsafeToFuture()
  }

  def removeSpeaker(talk: Talk.Slug, speaker: User.Slug): Action[AnyContent] = SecuredAction.async { implicit req =>
    val now = Instant.now()
    val next = Redirect(routes.TalkCtrl.detail(talk))
    (for {
      talkElt <- OptionT(talkRepo.find(user, talk))
      speakerElt <- OptionT(userRepo.find(speaker))
      res <- OptionT.liftF {
        talkRepo.removeSpeaker(user, talk)(speakerElt.id, now).flatMap { _ =>
          if (speakerElt.id == user) IO.pure(Redirect(UserRoutes.index()).flashing("success" -> s"You removed yourself from <b>$talk</b> talk"))
          else emailSrv.send(Emails.speakerRemovedFromTalk(talkElt, speakerElt, req.identity.user))
            .map(_ => next.flashing("success" -> s"<b>${speakerElt.name.value}</b> removed from speakers"))
        }.recover { case NonFatal(e) => next.flashing("error" -> s"<b>${speakerElt.name.value}</b> not removed: ${e.getMessage}") }
      }
    } yield res).value.map(_.getOrElse(talkNotFound(talk))).unsafeToFuture()
  }

  def doAddSlides(talk: Talk.Slug): Action[AnyContent] = SecuredAction.async { implicit req =>
    val now = Instant.now()
    val next = Redirect(routes.TalkCtrl.detail(talk))
    GenericForm.embed.bindFromRequest.fold(
      formWithErrors => IO.pure(next.flashing(formWithErrors.errors.map(e => "error" -> e.format): _*)),
      data => Slides.from(data) match {
        case Left(err) => IO.pure(next.flashing(err.errors.map(e => "error" -> e.value): _*))
        case Right(slides) => talkRepo.editSlides(user, talk)(slides, now).map(_ => next)
      }
    ).unsafeToFuture()
  }

  def doAddVideo(talk: Talk.Slug): Action[AnyContent] = SecuredAction.async { implicit req =>
    val now = Instant.now()
    val next = Redirect(routes.TalkCtrl.detail(talk))
    GenericForm.embed.bindFromRequest.fold(
      formWithErrors => IO.pure(next.flashing(formWithErrors.errors.map(e => "error" -> e.format): _*)),
      data => Video.from(data) match {
        case Left(err) => IO.pure(next.flashing(err.errors.map(e => "error" -> e.value): _*))
        case Right(video) => talkRepo.editVideo(user, talk)(video, now).map(_ => next)
      }
    ).unsafeToFuture()
  }

  def changeStatus(talk: Talk.Slug, status: Talk.Status): Action[AnyContent] = SecuredAction.async { implicit req =>
    talkRepo.editStatus(user, talk)(status)
      .map(_ => Redirect(routes.TalkCtrl.detail(talk)))
      .unsafeToFuture()
  }
}

object TalkCtrl {
  def listBreadcrumb(user: User): Breadcrumb =
    UserCtrl.breadcrumb(user).add("Talks" -> routes.TalkCtrl.list())

  def breadcrumb(user: User, talk: Talk): Breadcrumb =
    listBreadcrumb(user).add(talk.title.value -> routes.TalkCtrl.detail(talk.slug))
}
