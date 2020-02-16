package gospeak.web.pages.user.talks

import cats.data.OptionT
import cats.effect.IO
import com.mohiva.play.silhouette.api.Silhouette
import gospeak.core.domain.utils.UserCtx
import gospeak.core.domain._
import gospeak.core.services.email.EmailSrv
import gospeak.core.services.storage._
import gospeak.libs.scala.Extensions._
import gospeak.libs.scala.domain.{Page, Slides, Video}
import gospeak.web.AppConf
import gospeak.web.auth.domain.CookieEnv
import gospeak.web.domain.Breadcrumb
import gospeak.web.emails.Emails
import gospeak.web.pages.user.UserCtrl
import gospeak.web.pages.user.routes.{UserCtrl => UserRoutes}
import gospeak.web.pages.user.talks.TalkCtrl._
import gospeak.web.utils.{GsForms, UICtrl, UserReq}
import play.api.data.Form
import play.api.mvc._

import scala.util.control.NonFatal

class TalkCtrl(cc: ControllerComponents,
               silhouette: Silhouette[CookieEnv],
               conf: AppConf,
               userRepo: SpeakerUserRepo,
               userRequestRepo: SpeakerUserRequestRepo,
               eventRepo: SpeakerEventRepo,
               talkRepo: SpeakerTalkRepo,
               proposalRepo: SpeakerProposalRepo,
               externalEventRepo: SpeakerExternalEventRepo,
               externalProposalRepo: SpeakerExternalProposalRepo,
               emailSrv: EmailSrv) extends UICtrl(cc, silhouette, conf) {
  def list(params: Page.Params): Action[AnyContent] = UserAction { implicit req =>
    talkRepo.list(params).map(talks => Ok(html.list(talks)(listBreadcrumb)))
  }

  def create(): Action[AnyContent] = UserAction { implicit req =>
    createForm(GsForms.talk)
  }

  def doCreate(): Action[AnyContent] = UserAction { implicit req =>
    GsForms.talk.bindFromRequest.fold(
      formWithErrors => createForm(formWithErrors),
      data => talkRepo.find(data.slug).flatMap {
        case Some(duplicate) =>
          createForm(GsForms.talk.fillAndValidate(data).withError("slug", s"Slug already taken by talk: ${duplicate.title.value}"))
        case None =>
          talkRepo.create(data).map { _ => Redirect(routes.TalkCtrl.detail(data.slug)) }
      }
    )
  }

  private def createForm(form: Form[Talk.Data])(implicit req: UserReq[AnyContent]): IO[Result] = {
    IO.pure(Ok(html.create(form)(listBreadcrumb.add("New" -> routes.TalkCtrl.create()))))
  }

  def detail(talk: Talk.Slug): Action[AnyContent] = UserAction { implicit req =>
    (for {
      talkElt <- OptionT(talkRepo.find(talk))
      invites <- OptionT.liftF(userRequestRepo.listPendingInvites(talkElt.id))
      speakers <- OptionT.liftF(userRepo.list(talkElt.users))
      proposals <- OptionT.liftF(externalProposalRepo.listAllCommon(talkElt.id))
      b = breadcrumb(talkElt)
    } yield Ok(html.detail(talkElt, speakers, invites, proposals, GsForms.invite, GsForms.embed)(b))).value.map(_.getOrElse(talkNotFound(talk)))
  }

  def edit(talk: Talk.Slug, redirect: Option[String]): Action[AnyContent] = UserAction { implicit req =>
    editForm(talk, GsForms.talk, redirect)
  }

  def doEdit(talk: Talk.Slug, redirect: Option[String]): Action[AnyContent] = UserAction { implicit req =>
    GsForms.talk.bindFromRequest.fold(
      formWithErrors => editForm(talk, formWithErrors, redirect),
      data => talkRepo.find(data.slug).flatMap {
        case Some(duplicate) if data.slug != talk =>
          editForm(talk, GsForms.talk.fillAndValidate(data).withError("slug", s"Slug already taken by talk: ${duplicate.title.value}"), redirect)
        case _ => talkRepo.edit(talk, data).map { _ => redirectOr(redirect, routes.TalkCtrl.detail(data.slug)) }
      }
    )
  }

  private def editForm(talk: Talk.Slug, form: Form[Talk.Data], redirect: Option[String])(implicit req: UserReq[AnyContent], ctx: UserCtx): IO[Result] = {
    talkRepo.find(talk).map {
      case Some(talkElt) =>
        val filledForm = if (form.hasErrors) form else form.fill(talkElt.data)
        val b = breadcrumb(talkElt).add("Edit" -> routes.TalkCtrl.edit(talk))
        Ok(html.edit(talkElt, filledForm, redirect)(b))
      case None =>
        talkNotFound(talk)
    }
  }

  def inviteSpeaker(talk: Talk.Slug): Action[AnyContent] = UserAction { implicit req =>
    val next = Redirect(routes.TalkCtrl.detail(talk))
    GsForms.invite.bindFromRequest.fold(
      formWithErrors => IO.pure(next.flashing("error" -> req.formatErrors(formWithErrors))),
      email => (for {
        talkElt <- OptionT(talkRepo.find(talk))
        invite <- OptionT.liftF(userRequestRepo.invite(talkElt.id, email))
        _ <- OptionT.liftF(emailSrv.send(Emails.inviteSpeakerToTalk(invite, talkElt)))
      } yield next.flashing("success" -> s"<b>$email</b> is invited as speaker")).value.map(_.getOrElse(talkNotFound(talk)))
    )
  }

  def cancelInviteSpeaker(talk: Talk.Slug, request: UserRequest.Id): Action[AnyContent] = UserAction { implicit req =>
    (for {
      talkElt <- OptionT(talkRepo.find(talk))
      invite <- OptionT.liftF(userRequestRepo.cancelTalkInvite(request))
      _ <- OptionT.liftF(emailSrv.send(Emails.inviteSpeakerToTalkCanceled(invite, talkElt)))
      next = Redirect(routes.TalkCtrl.detail(talk)).flashing("success" -> s"Invitation to <b>${invite.email.value}</b> has been canceled")
    } yield next).value.map(_.getOrElse(talkNotFound(talk)))
  }

  def removeSpeaker(talk: Talk.Slug, speaker: User.Slug): Action[AnyContent] = UserAction { implicit req =>
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
  }

  def doAddSlides(talk: Talk.Slug): Action[AnyContent] = UserAction { implicit req =>
    val next = Redirect(routes.TalkCtrl.detail(talk))
    GsForms.embed.bindFromRequest.fold(
      formWithErrors => IO.pure(next.flashing("error" -> req.formatErrors(formWithErrors))),
      data => Slides.from(data) match {
        case Left(err) => IO.pure(next.flashing("error" -> err.getMessage))
        case Right(slides) => talkRepo.editSlides(talk, slides).map(_ => next)
      }
    )
  }

  def doAddVideo(talk: Talk.Slug): Action[AnyContent] = UserAction { implicit req =>
    val next = Redirect(routes.TalkCtrl.detail(talk))
    GsForms.embed.bindFromRequest.fold(
      formWithErrors => IO.pure(next.flashing("error" -> req.formatErrors(formWithErrors))),
      data => Video.from(data) match {
        case Left(err) => IO.pure(next.flashing("error" -> err.getMessage))
        case Right(video) => talkRepo.editVideo(talk, video).map(_ => next)
      }
    )
  }

  def changeStatus(talk: Talk.Slug, status: Talk.Status): Action[AnyContent] = UserAction { implicit req =>
    talkRepo.editStatus(talk, status).map(_ => Redirect(routes.TalkCtrl.detail(talk)))
  }

  def findExternalEvent(talk: Talk.Slug, params: Page.Params): Action[AnyContent] = UserAction { implicit req =>
    (for {
      talkElt <- OptionT(talkRepo.find(talk))
      events <- OptionT.liftF(externalEventRepo.list(params))
      res = Ok(html.findExternalEvent(talkElt, events)(extEventsBreadcrumb(talkElt)))
    } yield res).value.map(_.getOrElse(talkNotFound(talk)))
  }

  def createExternalEvent(talk: Talk.Slug): Action[AnyContent] = UserAction { implicit req =>
    createExternalEventView(talk, GsForms.externalEvent)
  }

  def doCreateExternalEvent(talk: Talk.Slug): Action[AnyContent] = UserAction { implicit req =>
    GsForms.externalEvent.bindFromRequest.fold(
      formWithErrors => createExternalEventView(talk, formWithErrors),
      data => externalEventRepo.create(data).map(e => Redirect(routes.TalkCtrl.createExternalProposal(talk, e.id)))
    )
  }

  private def createExternalEventView(talk: Talk.Slug, form: Form[ExternalEvent.Data])(implicit req: UserReq[AnyContent]): IO[Result] = {
    (for {
      talkElt <- OptionT(talkRepo.find(talk))
      res = Ok(html.createExternalEvent(talkElt, form)(extEventsBreadcrumb(talkElt).add("New" -> routes.TalkCtrl.createExternalEvent(talk))))
    } yield res).value.map(_.getOrElse(talkNotFound(talk)))
  }

  def createExternalProposal(talk: Talk.Slug, event: ExternalEvent.Id): Action[AnyContent] = UserAction { implicit req =>
    createExternalProposalView(talk, event, GsForms.externalProposal)
  }

  def doCreateExternalProposal(talk: Talk.Slug, event: ExternalEvent.Id): Action[AnyContent] = UserAction { implicit req =>
    GsForms.externalProposal.bindFromRequest.fold(
      formWithErrors => createExternalProposalView(talk, event, formWithErrors),
      data => (for {
        talkElt <- OptionT(talkRepo.find(talk))
        _ <- OptionT.liftF(externalProposalRepo.create(talkElt.id, event, data, talkElt.speakers))
      } yield Redirect(routes.TalkCtrl.detail(talk))).value.map(_.getOrElse(talkNotFound(talk)))
    )
  }

  private def createExternalProposalView(talk: Talk.Slug, event: ExternalEvent.Id, form: Form[ExternalProposal.Data])(implicit req: UserReq[AnyContent]): IO[Result] = {
    (for {
      talkElt <- OptionT(talkRepo.find(talk))
      eventElt <- OptionT(externalEventRepo.find(event))
      filledForm = if (form.hasErrors) form else form.fill(ExternalProposal.Data(talkElt))
      res = Ok(html.createExternalProposal(talkElt, eventElt, filledForm)(breadcrumb(talkElt).add("Add proposal" -> routes.TalkCtrl.createExternalProposal(talk, event))))
    } yield res).value.map(_.getOrElse(extEventNotFound(talk, event)))
  }
}

object TalkCtrl {
  def listBreadcrumb(implicit req: UserReq[AnyContent]): Breadcrumb =
    UserCtrl.breadcrumb.add("Talks" -> routes.TalkCtrl.list())

  def breadcrumb(talk: Talk)(implicit req: UserReq[AnyContent]): Breadcrumb =
    listBreadcrumb.addOpt(talk.title.value -> Some(routes.TalkCtrl.detail(talk.slug)).filter(_ => talk.hasSpeaker(req.user.id)))

  def extEventsBreadcrumb(talk: Talk)(implicit req: UserReq[AnyContent]): Breadcrumb =
    breadcrumb(talk).add("Events" -> routes.TalkCtrl.findExternalEvent(talk.slug))

  def extEventBredcrumb(talk: Talk, event: ExternalEvent)(implicit req: UserReq[AnyContent]): Breadcrumb =
    extEventsBreadcrumb(talk).add(event.name.value -> routes.TalkCtrl.findExternalEvent(talk.slug))
}
