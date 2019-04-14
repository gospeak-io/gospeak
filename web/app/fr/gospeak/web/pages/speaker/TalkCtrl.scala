package fr.gospeak.web.pages.speaker

import java.time.Instant

import cats.data.OptionT
import cats.effect.IO
import com.mohiva.play.silhouette.api.Silhouette
import com.mohiva.play.silhouette.api.actions.SecuredRequest
import fr.gospeak.core.domain.{Talk, User}
import fr.gospeak.core.services._
import fr.gospeak.libs.scalautils.domain.{Page, Slides, Video}
import fr.gospeak.web.auth.domain.CookieEnv
import fr.gospeak.web.domain.Breadcrumb
import fr.gospeak.web.pages.speaker.TalkCtrl._
import fr.gospeak.web.pages.user.UserCtrl
import fr.gospeak.web.utils.{GenericForm, UICtrl}
import play.api.data.Form
import play.api.mvc._

class TalkCtrl(cc: ControllerComponents,
               silhouette: Silhouette[CookieEnv],
               userRepo: SpeakerUserRepo,
               eventRepo: SpeakerEventRepo,
               talkRepo: SpeakerTalkRepo,
               proposalRepo: SpeakerProposalRepo) extends UICtrl(cc, silhouette) {

  import silhouette._

  def list(params: Page.Params): Action[AnyContent] = SecuredAction.async { implicit req =>
    (for {
      talks <- talkRepo.list(req.identity.user.id, params)
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
      data => talkRepo.find(req.identity.user.id, data.slug).flatMap {
        case Some(duplicate) =>
          createForm(TalkForms.create.fillAndValidate(data).withError("slug", s"Slug already taken by talk: ${duplicate.title.value}"))
        case None =>
          talkRepo.create(req.identity.user.id, data, now).map { _ => Redirect(routes.TalkCtrl.detail(data.slug)) }
      }
    ).unsafeToFuture()
  }

  private def createForm(form: Form[Talk.Data])(implicit req: SecuredRequest[CookieEnv, AnyContent]): IO[Result] = {
    val b = listBreadcrumb(req.identity.user).add("New" -> routes.TalkCtrl.create())
    IO.pure(Ok(html.create(form)(b)))
  }

  def detail(talk: Talk.Slug): Action[AnyContent] = SecuredAction.async { implicit req =>
    (for {
      talkElt <- OptionT(talkRepo.find(req.identity.user.id, talk))
      speakers <- OptionT.liftF(userRepo.list(talkElt.speakers.toList))
      proposals <- OptionT.liftF(proposalRepo.list(talkElt.id, Page.Params.defaults))
      events <- OptionT.liftF(eventRepo.list(proposals.items.flatMap(_._2.event)))
      b = breadcrumb(req.identity.user, talkElt)
    } yield Ok(html.detail(talkElt, speakers, proposals, events, GenericForm.embed)(b))).value.map(_.getOrElse(talkNotFound(talk))).unsafeToFuture()
  }

  def doAddSlides(talk: Talk.Slug): Action[AnyContent] = SecuredAction.async { implicit req =>
    val now = Instant.now()
    val next = Redirect(routes.TalkCtrl.detail(talk))
    GenericForm.embed.bindFromRequest.fold(
      formWithErrors => IO.pure(next.flashing(formWithErrors.errors.map(e => "error" -> e.format): _*)),
      data => Slides.from(data) match {
        case Left(err) => IO.pure(next.flashing(err.errors.map(e => "error" -> e.value): _*))
        case Right(slides) => talkRepo.editSlides(req.identity.user.id, talk)(slides, now).map(_ => next)
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
        case Right(video) => talkRepo.editVideo(req.identity.user.id, talk)(video, now).map(_ => next)
      }
    ).unsafeToFuture()
  }

  def edit(talk: Talk.Slug): Action[AnyContent] = SecuredAction.async { implicit req =>
    editForm(talk, TalkForms.create).unsafeToFuture()
  }

  def doEdit(talk: Talk.Slug): Action[AnyContent] = SecuredAction.async { implicit req =>
    val now = Instant.now()
    TalkForms.create.bindFromRequest.fold(
      formWithErrors => editForm(talk, formWithErrors),
      data => talkRepo.find(req.identity.user.id, data.slug).flatMap {
        case Some(duplicate) if data.slug != talk =>
          editForm(talk, TalkForms.create.fillAndValidate(data).withError("slug", s"Slug already taken by talk: ${duplicate.title.value}"))
        case _ =>
          talkRepo.edit(req.identity.user.id, talk)(data, now).map { _ => Redirect(routes.TalkCtrl.detail(data.slug)) }
      }
    ).unsafeToFuture()
  }

  private def editForm(talk: Talk.Slug, form: Form[Talk.Data])(implicit req: SecuredRequest[CookieEnv, AnyContent]): IO[Result] = {
    talkRepo.find(req.identity.user.id, talk).map {
      case Some(talkElt) =>
        val b = breadcrumb(req.identity.user, talkElt).add("Edit" -> routes.TalkCtrl.edit(talk))
        val filledForm = if (form.hasErrors) form else form.fill(talkElt.data)
        Ok(html.edit(talkElt, filledForm)(b))
      case None =>
        talkNotFound(talk)
    }
  }

  def changeStatus(talk: Talk.Slug, status: Talk.Status): Action[AnyContent] = SecuredAction.async { implicit req =>
    talkRepo.editStatus(req.identity.user.id, talk)(status)
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
