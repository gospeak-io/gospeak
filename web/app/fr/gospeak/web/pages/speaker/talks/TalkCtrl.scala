package fr.gospeak.web.pages.speaker.talks

import cats.data.OptionT
import cats.effect.IO
import com.mohiva.play.silhouette.api.Silhouette
import fr.gospeak.core.ApplicationConf
import fr.gospeak.core.domain.{Talk, User, UserRequest}
import fr.gospeak.core.services.storage._
import fr.gospeak.infra.services.EmailSrv
import fr.gospeak.libs.scalautils.Extensions._
import fr.gospeak.libs.scalautils.domain.{Page, Slides, Video}
import fr.gospeak.web.auth.domain.CookieEnv
import fr.gospeak.web.domain.Breadcrumb
import fr.gospeak.web.emails.Emails
import fr.gospeak.web.pages.speaker.talks.TalkCtrl._
import fr.gospeak.web.pages.user.UserCtrl
import fr.gospeak.web.pages.user.routes.{UserCtrl => UserRoutes}
import fr.gospeak.web.utils.{GenericForm, SecuredReq, UICtrl}
import play.api.data.Form
import play.api.mvc._

import scala.util.control.NonFatal

class TalkCtrl(cc: ControllerComponents,
               silhouette: Silhouette[CookieEnv],
               env: ApplicationConf.Env,
               userRepo: SpeakerUserRepo,
               userRequestRepo: SpeakerUserRequestRepo,
               eventRepo: SpeakerEventRepo,
               talkRepo: SpeakerTalkRepo,
               proposalRepo: SpeakerProposalRepo,
               emailSrv: EmailSrv) extends UICtrl(cc, silhouette, env) {
  def list(params: Page.Params): Action[AnyContent] = SecuredActionIO { implicit req =>
    for {
      talks <- talkRepo.list(req.user.id, params)
      b = listBreadcrumb(req.user)
    } yield Ok(html.list(talks)(b))
  }

  def create(): Action[AnyContent] = SecuredActionIO { implicit req =>
    createForm(TalkForms.create)
  }

  def doCreate(): Action[AnyContent] = SecuredActionIO { implicit req =>
    TalkForms.create.bindFromRequest.fold(
      formWithErrors => createForm(formWithErrors),
      data => talkRepo.find(req.user.id, data.slug).flatMap {
        case Some(duplicate) =>
          createForm(TalkForms.create.fillAndValidate(data).withError("slug", s"Slug already taken by talk: ${duplicate.title.value}"))
        case None =>
          talkRepo.create(data, req.user.id, req.now).map { _ => Redirect(routes.TalkCtrl.detail(data.slug)) }
      }
    )
  }

  private def createForm(form: Form[Talk.Data])(implicit req: SecuredReq[AnyContent]): IO[Result] = {
    val b = listBreadcrumb(req.user).add("New" -> routes.TalkCtrl.create())
    IO.pure(Ok(html.create(form)(b)))
  }

  def detail(talk: Talk.Slug): Action[AnyContent] = SecuredActionIO { implicit req =>
    (for {
      talkElt <- OptionT(talkRepo.find(req.user.id, talk))
      invites <- OptionT.liftF(userRequestRepo.listPendingInvites(talkElt.id))
      speakers <- OptionT.liftF(userRepo.list(talkElt.users))
      proposals <- OptionT.liftF(proposalRepo.listFull(talkElt.id, Page.Params.defaults))
      b = breadcrumb(req.user, talkElt)
    } yield Ok(html.detail(talkElt, speakers, invites, proposals, GenericForm.invite, GenericForm.embed)(b))).value.map(_.getOrElse(talkNotFound(talk)))
  }

  def edit(talk: Talk.Slug): Action[AnyContent] = SecuredActionIO { implicit req =>
    editForm(talk, TalkForms.create)
  }

  def doEdit(talk: Talk.Slug): Action[AnyContent] = SecuredActionIO { implicit req =>
    TalkForms.create.bindFromRequest.fold(
      formWithErrors => editForm(talk, formWithErrors),
      data => talkRepo.find(req.user.id, data.slug).flatMap {
        case Some(duplicate) if data.slug != talk =>
          editForm(talk, TalkForms.create.fillAndValidate(data).withError("slug", s"Slug already taken by talk: ${duplicate.title.value}"))
        case _ =>
          talkRepo.edit(talk)(data, req.user.id, req.now).map { _ => Redirect(routes.TalkCtrl.detail(data.slug)) }
      }
    )
  }

  private def editForm(talk: Talk.Slug, form: Form[Talk.Data])(implicit req: SecuredReq[AnyContent]): IO[Result] = {
    talkRepo.find(req.user.id, talk).map {
      case Some(talkElt) =>
        val b = breadcrumb(req.user, talkElt).add("Edit" -> routes.TalkCtrl.edit(talk))
        val filledForm = if (form.hasErrors) form else form.fill(talkElt.data)
        Ok(html.edit(talkElt, filledForm)(b))
      case None =>
        talkNotFound(talk)
    }
  }

  def inviteSpeaker(talk: Talk.Slug): Action[AnyContent] = SecuredActionIO { implicit req =>
    val next = Redirect(routes.TalkCtrl.detail(talk))
    GenericForm.invite.bindFromRequest.fold(
      formWithErrors => IO.pure(next.flashing("error" -> req.formatErrors(formWithErrors))),
      email => (for {
        talkElt <- OptionT(talkRepo.find(req.user.id, talk))
        invite <- OptionT.liftF(userRequestRepo.invite(talkElt.id, email, req.user.id, req.now))
        _ <- OptionT.liftF(emailSrv.send(Emails.inviteSpeakerToTalk(invite, talkElt)))
      } yield next.flashing("success" -> s"<b>$email</b> is invited as speaker")).value.map(_.getOrElse(talkNotFound(talk)))
    )
  }

  def cancelInviteSpeaker(talk: Talk.Slug, request: UserRequest.Id): Action[AnyContent] = SecuredActionIO { implicit req =>
    (for {
      talkElt <- OptionT(talkRepo.find(req.user.id, talk))
      invite <- OptionT.liftF(userRequestRepo.cancelTalkInvite(request, req.user.id, req.now))
      _ <- OptionT.liftF(emailSrv.send(Emails.inviteSpeakerToTalkCanceled(invite, talkElt)))
      next = Redirect(routes.TalkCtrl.detail(talk)).flashing("success" -> s"Invitation to <b>${invite.email.value}</b> has been canceled")
    } yield next).value.map(_.getOrElse(talkNotFound(talk)))
  }

  def removeSpeaker(talk: Talk.Slug, speaker: User.Slug): Action[AnyContent] = SecuredActionIO { implicit req =>
    val next = Redirect(routes.TalkCtrl.detail(talk))
    (for {
      talkElt <- OptionT(talkRepo.find(req.user.id, talk))
      speakerElt <- OptionT(userRepo.find(speaker))
      res <- OptionT.liftF {
        talkRepo.removeSpeaker(talk)(speakerElt.id, req.user.id, req.now).flatMap { _ =>
          if (speakerElt.id == req.user.id) IO.pure(Redirect(UserRoutes.index()).flashing("success" -> s"You removed yourself from <b>$talk</b> talk"))
          else emailSrv.send(Emails.speakerRemovedFromTalk(talkElt, speakerElt))
            .map(_ => next.flashing("success" -> s"<b>${speakerElt.name.value}</b> removed from speakers"))
        }.recover { case NonFatal(e) => next.flashing("error" -> s"<b>${speakerElt.name.value}</b> not removed: ${e.getMessage}") }
      }
    } yield res).value.map(_.getOrElse(talkNotFound(talk)))
  }

  def doAddSlides(talk: Talk.Slug): Action[AnyContent] = SecuredActionIO { implicit req =>
    val next = Redirect(routes.TalkCtrl.detail(talk))
    GenericForm.embed.bindFromRequest.fold(
      formWithErrors => IO.pure(next.flashing("error" -> req.formatErrors(formWithErrors))),
      data => Slides.from(data) match {
        case Left(err) => IO.pure(next.flashing("error" -> err.getMessage))
        case Right(slides) => talkRepo.editSlides(talk)(slides, req.user.id, req.now).map(_ => next)
      }
    )
  }

  def doAddVideo(talk: Talk.Slug): Action[AnyContent] = SecuredActionIO { implicit req =>
    val next = Redirect(routes.TalkCtrl.detail(talk))
    GenericForm.embed.bindFromRequest.fold(
      formWithErrors => IO.pure(next.flashing("error" -> req.formatErrors(formWithErrors))),
      data => Video.from(data) match {
        case Left(err) => IO.pure(next.flashing("error" -> err.getMessage))
        case Right(video) => talkRepo.editVideo(talk)(video, req.user.id, req.now).map(_ => next)
      }
    )
  }

  def changeStatus(talk: Talk.Slug, status: Talk.Status): Action[AnyContent] = SecuredActionIO { implicit req =>
    talkRepo.editStatus(talk)(status, req.user.id)
      .map(_ => Redirect(routes.TalkCtrl.detail(talk)))
  }
}

object TalkCtrl {
  def listBreadcrumb(user: User): Breadcrumb =
    UserCtrl.breadcrumb(user).add("Talks" -> routes.TalkCtrl.list())

  def breadcrumb(user: User, talk: Talk): Breadcrumb =
    listBreadcrumb(user).addOpt(talk.title.value -> Some(routes.TalkCtrl.detail(talk.slug)).filter(_ => talk.hasSpeaker(user.id)))
}
