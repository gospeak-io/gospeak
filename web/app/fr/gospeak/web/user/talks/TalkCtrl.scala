package fr.gospeak.web.user.talks

import java.time.Instant

import cats.data.OptionT
import cats.effect.IO
import fr.gospeak.core.domain.{Talk, User}
import fr.gospeak.core.services.GospeakDb
import fr.gospeak.libs.scalautils.domain.{Page, Slides, Video}
import fr.gospeak.web.auth.AuthService
import fr.gospeak.web.domain.{Breadcrumb, HeaderInfo, NavLink}
import fr.gospeak.web.user.UserCtrl
import fr.gospeak.web.user.talks.TalkCtrl._
import fr.gospeak.web.utils.{GenericForm, UICtrl}
import play.api.data.Form
import play.api.mvc._

class TalkCtrl(cc: ControllerComponents, db: GospeakDb, auth: AuthService) extends UICtrl(cc) {
  def list(params: Page.Params): Action[AnyContent] = Action.async { implicit req: Request[AnyContent] =>
    implicit val user: User = auth.authed()
    (for {
      talks <- db.getTalks(user.id, params)
      h = listHeader
      b = listBreadcrumb(user.name)
    } yield Ok(html.list(talks)(h, b))).unsafeToFuture()
  }

  def create(): Action[AnyContent] = Action.async { implicit req: Request[AnyContent] =>
    implicit val user: User = auth.authed()
    createForm(TalkForms.create).unsafeToFuture()
  }

  def doCreate(): Action[AnyContent] = Action.async { implicit req: Request[AnyContent] =>
    implicit val user: User = auth.authed()
    val now = Instant.now()
    TalkForms.create.bindFromRequest.fold(
      formWithErrors => createForm(formWithErrors),
      data => db.getTalk(user.id, data.slug).flatMap {
        case Some(duplicate) =>
          createForm(TalkForms.create.fillAndValidate(data).withError("slug", s"Slug already taken by talk: ${duplicate.title.value}"))
        case None =>
          db.createTalk(user.id, data, now).map { _ => Redirect(routes.TalkCtrl.detail(data.slug)) }
      }
    ).unsafeToFuture()
  }

  private def createForm(form: Form[Talk.Data])(implicit req: Request[AnyContent], user: User): IO[Result] = {
    val h = listHeader
    val b = listBreadcrumb(user.name).add("New" -> routes.TalkCtrl.create())
    IO.pure(Ok(html.create(form)(h, b)))
  }

  def detail(talk: Talk.Slug): Action[AnyContent] = Action.async { implicit req: Request[AnyContent] =>
    implicit val user: User = auth.authed()
    (for {
      talkElt <- OptionT(db.getTalk(user.id, talk))
      speakers <- OptionT.liftF(db.getUsers(talkElt.speakers.toList))
      proposals <- OptionT.liftF(db.getProposals(talkElt.id, Page.Params.defaults))
      events <- OptionT.liftF(db.getEvents(proposals.items.flatMap(_._2.event)))
      h = header(talk)
      b = breadcrumb(user.name, talk -> talkElt.title)
    } yield Ok(html.detail(talkElt, speakers, proposals, events, GenericForm.embed)(h, b))).value.map(_.getOrElse(talkNotFound(talk))).unsafeToFuture()
  }

  def doAddSlides(talk: Talk.Slug): Action[AnyContent] = Action.async { implicit req: Request[AnyContent] =>
    implicit val user: User = auth.authed()
    val now = Instant.now()
    val next = Redirect(routes.TalkCtrl.detail(talk))
    GenericForm.embed.bindFromRequest.fold(
      formWithErrors => IO.pure(next.flashing(formWithErrors.errors.map(e => "error" -> e.format): _*)),
      data => Slides.from(data) match {
        case Left(err) => IO.pure(next.flashing(err.errors.map(e => "error" -> e.value): _*))
        case Right(slides) => db.updateTalkSlides(user.id, talk)(slides, now).map(_ => next)
      }
    ).unsafeToFuture()
  }

  def doAddVideo(talk: Talk.Slug): Action[AnyContent] = Action.async { implicit req: Request[AnyContent] =>
    implicit val user: User = auth.authed()
    val now = Instant.now()
    val next = Redirect(routes.TalkCtrl.detail(talk))
    GenericForm.embed.bindFromRequest.fold(
      formWithErrors => IO.pure(next.flashing(formWithErrors.errors.map(e => "error" -> e.format): _*)),
      data => Video.from(data) match {
        case Left(err) => IO.pure(next.flashing(err.errors.map(e => "error" -> e.value): _*))
        case Right(video) => db.updateTalkVideo(user.id, talk)(video, now).map(_ => next)
      }
    ).unsafeToFuture()
  }

  def edit(talk: Talk.Slug): Action[AnyContent] = Action.async { implicit req: Request[AnyContent] =>
    implicit val user: User = auth.authed()
    editForm(talk, TalkForms.create).unsafeToFuture()
  }

  def doEdit(talk: Talk.Slug): Action[AnyContent] = Action.async { implicit req: Request[AnyContent] =>
    implicit val user: User = auth.authed()
    val now = Instant.now()
    TalkForms.create.bindFromRequest.fold(
      formWithErrors => editForm(talk, formWithErrors),
      data => db.getTalk(user.id, data.slug).flatMap {
        case Some(duplicate) if data.slug != talk =>
          editForm(talk, TalkForms.create.fillAndValidate(data).withError("slug", s"Slug already taken by talk: ${duplicate.title.value}"))
        case _ =>
          db.updateTalk(user.id, talk)(data, now).map { _ => Redirect(routes.TalkCtrl.detail(data.slug)) }
      }
    ).unsafeToFuture()
  }

  private def editForm(talk: Talk.Slug, form: Form[Talk.Data])(implicit req: Request[AnyContent], user: User): IO[Result] = {
    db.getTalk(user.id, talk).map {
      case Some(talkElt) =>
        val h = header(talkElt.slug)
        val b = breadcrumb(user.name, talkElt.slug -> talkElt.title).add("Edit" -> routes.TalkCtrl.edit(talk))
        val filledForm = if (form.hasErrors) form else form.fill(talkElt.data)
        Ok(html.edit(talkElt, filledForm)(h, b))
      case None =>
        talkNotFound(talk)
    }
  }

  def changeStatus(talk: Talk.Slug, status: Talk.Status): Action[AnyContent] = Action.async { implicit req: Request[AnyContent] =>
    implicit val user: User = auth.authed()
    db.updateTalkStatus(user.id, talk)(status)
      .map(_ => Redirect(routes.TalkCtrl.detail(talk)))
      .unsafeToFuture()
  }
}

object TalkCtrl {
  def listHeader: HeaderInfo =
    UserCtrl.header.activeFor(routes.TalkCtrl.list())

  def listBreadcrumb(user: User.Name): Breadcrumb =
    UserCtrl.breadcrumb(user).add("Talks" -> routes.TalkCtrl.list())

  def header(talk: Talk.Slug): HeaderInfo =
    UserCtrl.header
      .copy(brand = NavLink("Gospeak", fr.gospeak.web.user.routes.UserCtrl.index()))
      .activeFor(routes.TalkCtrl.list())

  def breadcrumb(user: User.Name, talk: (Talk.Slug, Talk.Title)): Breadcrumb =
    talk match {
      case (talkSlug, talkTitle) => listBreadcrumb(user).add(talkTitle.value -> routes.TalkCtrl.detail(talkSlug))
    }
}
