package fr.gospeak.web.pages.speaker.talks

import cats.data.OptionT
import cats.effect.IO
import com.mohiva.play.silhouette.api.Silhouette
import fr.gospeak.core.ApplicationConf
import fr.gospeak.core.domain.utils.UserCtx
import fr.gospeak.core.domain.{Talk, User, UserRequest}
import fr.gospeak.core.services.email.EmailSrv
import fr.gospeak.core.services.storage._
import fr.gospeak.libs.scalautils.Extensions._
import fr.gospeak.libs.scalautils.domain.{Page, Slides, Video}
import fr.gospeak.web.auth.domain.CookieEnv
import fr.gospeak.web.domain.Breadcrumb
import fr.gospeak.web.emails.Emails
import fr.gospeak.web.pages.speaker.talks.TalkCtrl._
import fr.gospeak.web.pages.user.UserCtrl
import fr.gospeak.web.pages.user.routes.{UserCtrl => UserRoutes}
import fr.gospeak.web.utils.{GenericForm, UICtrl, UserReq}
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
               emailSrv: EmailSrv) extends UICtrl(cc, silhouette, env) with UICtrl.UserAction {
  def list(params: Page.Params): Action[AnyContent] = UserAction(implicit req => implicit ctx => {
    talkRepo.list(params).map(talks => Ok(html.list(talks)(listBreadcrumb)))
  })

  def create(): Action[AnyContent] = UserAction(implicit req => implicit ctx => {
    createForm(TalkForms.create)
  })

  def doCreate(): Action[AnyContent] = UserAction(implicit req => implicit ctx => {
    TalkForms.create.bindFromRequest.fold(
      formWithErrors => createForm(formWithErrors),
      data => talkRepo.find(data.slug).flatMap {
        case Some(duplicate) =>
          createForm(TalkForms.create.fillAndValidate(data).withError("slug", s"Slug already taken by talk: ${duplicate.title.value}"))
        case None =>
          talkRepo.create(data).map { _ => Redirect(routes.TalkCtrl.detail(data.slug)) }
      }
    )
  })

  private def createForm(form: Form[Talk.Data])(implicit req: UserReq[AnyContent]): IO[Result] = {
    IO.pure(Ok(html.create(form)(listBreadcrumb.add("New" -> routes.TalkCtrl.create()))))
  }

  def detail(talk: Talk.Slug): Action[AnyContent] = UserAction(implicit req => implicit ctx => {
    (for {
      talkElt <- OptionT(talkRepo.find(talk))
      invites <- OptionT.liftF(userRequestRepo.listPendingInvites(talkElt.id))
      speakers <- OptionT.liftF(userRepo.list(talkElt.users))
      proposals <- OptionT.liftF(proposalRepo.listFull(talkElt.id, Page.Params.defaults))
      b = breadcrumb(talkElt)
    } yield Ok(html.detail(talkElt, speakers, invites, proposals, GenericForm.invite, GenericForm.embed)(b))).value.map(_.getOrElse(talkNotFound(talk)))
  })

  def edit(talk: Talk.Slug): Action[AnyContent] = UserAction(implicit req => implicit ctx => {
    editForm(talk, TalkForms.create)
  })

  def doEdit(talk: Talk.Slug): Action[AnyContent] = UserAction(implicit req => implicit ctx => {
    TalkForms.create.bindFromRequest.fold(
      formWithErrors => editForm(talk, formWithErrors),
      data => talkRepo.find(data.slug).flatMap {
        case Some(duplicate) if data.slug != talk =>
          editForm(talk, TalkForms.create.fillAndValidate(data).withError("slug", s"Slug already taken by talk: ${duplicate.title.value}"))
        case _ =>
          talkRepo.edit(talk, data).map { _ => Redirect(routes.TalkCtrl.detail(data.slug)) }
      }
    )
  })

  private def editForm(talk: Talk.Slug, form: Form[Talk.Data])(implicit req: UserReq[AnyContent], ctx: UserCtx): IO[Result] = {
    talkRepo.find(talk).map {
      case Some(talkElt) =>
        val filledForm = if (form.hasErrors) form else form.fill(talkElt.data)
        val b = breadcrumb(talkElt).add("Edit" -> routes.TalkCtrl.edit(talk))
        Ok(html.edit(talkElt, filledForm)(b))
      case None =>
        talkNotFound(talk)
    }
  }

  def inviteSpeaker(talk: Talk.Slug): Action[AnyContent] = UserAction(implicit req => implicit ctx => {
    val next = Redirect(routes.TalkCtrl.detail(talk))
    GenericForm.invite.bindFromRequest.fold(
      formWithErrors => IO.pure(next.flashing("error" -> req.formatErrors(formWithErrors))),
      email => (for {
        talkElt <- OptionT(talkRepo.find(talk))
        invite <- OptionT.liftF(userRequestRepo.invite(talkElt.id, email))
        _ <- OptionT.liftF(emailSrv.send(Emails.inviteSpeakerToTalk(invite, talkElt)))
      } yield next.flashing("success" -> s"<b>$email</b> is invited as speaker")).value.map(_.getOrElse(talkNotFound(talk)))
    )
  })

  def cancelInviteSpeaker(talk: Talk.Slug, request: UserRequest.Id): Action[AnyContent] = UserAction(implicit req => implicit ctx => {
    (for {
      talkElt <- OptionT(talkRepo.find(talk))
      invite <- OptionT.liftF(userRequestRepo.cancelTalkInvite(request))
      _ <- OptionT.liftF(emailSrv.send(Emails.inviteSpeakerToTalkCanceled(invite, talkElt)))
      next = Redirect(routes.TalkCtrl.detail(talk)).flashing("success" -> s"Invitation to <b>${invite.email.value}</b> has been canceled")
    } yield next).value.map(_.getOrElse(talkNotFound(talk)))
  })

  def removeSpeaker(talk: Talk.Slug, speaker: User.Slug): Action[AnyContent] = UserAction(implicit req => implicit ctx => {
    val next = Redirect(routes.TalkCtrl.detail(talk))
    (for {
      talkElt <- OptionT(talkRepo.find(talk))
      speakerElt <- OptionT(userRepo.find(speaker))
      res <- OptionT.liftF {
        talkRepo.removeSpeaker(talk, speakerElt.id).flatMap { _ =>
          if (speakerElt.id == req.user.id) IO.pure(Redirect(UserRoutes.index()).flashing("success" -> s"You removed yourself from <b>$talk</b> talk"))
          else emailSrv.send(Emails.speakerRemovedFromTalk(talkElt, speakerElt))
            .map(_ => next.flashing("success" -> s"<b>${speakerElt.name.value}</b> removed from speakers"))
        }.recover { case NonFatal(e) => next.flashing("error" -> s"<b>${speakerElt.name.value}</b> not removed: ${e.getMessage}") }
      }
    } yield res).value.map(_.getOrElse(talkNotFound(talk)))
  })

  def doAddSlides(talk: Talk.Slug): Action[AnyContent] = UserAction(implicit req => implicit ctx => {
    val next = Redirect(routes.TalkCtrl.detail(talk))
    GenericForm.embed.bindFromRequest.fold(
      formWithErrors => IO.pure(next.flashing("error" -> req.formatErrors(formWithErrors))),
      data => Slides.from(data) match {
        case Left(err) => IO.pure(next.flashing("error" -> err.getMessage))
        case Right(slides) => talkRepo.editSlides(talk, slides).map(_ => next)
      }
    )
  })

  def doAddVideo(talk: Talk.Slug): Action[AnyContent] = UserAction(implicit req => implicit ctx => {
    val next = Redirect(routes.TalkCtrl.detail(talk))
    GenericForm.embed.bindFromRequest.fold(
      formWithErrors => IO.pure(next.flashing("error" -> req.formatErrors(formWithErrors))),
      data => Video.from(data) match {
        case Left(err) => IO.pure(next.flashing("error" -> err.getMessage))
        case Right(video) => talkRepo.editVideo(talk, video).map(_ => next)
      }
    )
  })

  def changeStatus(talk: Talk.Slug, status: Talk.Status): Action[AnyContent] = UserAction(implicit req => implicit ctx => {
    talkRepo.editStatus(talk, status).map(_ => Redirect(routes.TalkCtrl.detail(talk)))
  })
}

object TalkCtrl {
  def listBreadcrumb(implicit req: UserReq[AnyContent]): Breadcrumb =
    UserCtrl.breadcrumb.add("Talks" -> routes.TalkCtrl.list())

  def breadcrumb(talk: Talk)(implicit req: UserReq[AnyContent]): Breadcrumb =
    listBreadcrumb.addOpt(talk.title.value -> Some(routes.TalkCtrl.detail(talk.slug)).filter(_ => talk.hasSpeaker(req.user.id)))
}
