package fr.gospeak.web.user.talks

import cats.data.OptionT
import cats.effect.IO
import fr.gospeak.core.domain.utils.Page
import fr.gospeak.core.domain.{Talk, User}
import fr.gospeak.core.services.GospeakDb
import fr.gospeak.web.auth.AuthService
import fr.gospeak.web.domain.{Breadcrumb, HeaderInfo, NavLink}
import fr.gospeak.web.user.UserCtrl
import fr.gospeak.web.user.talks.TalkCtrl._
import fr.gospeak.web.utils.UICtrl
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
    TalkForms.create.bindFromRequest.fold(
      formWithErrors => createForm(formWithErrors),
      data => db.getTalk(user.id, data.slug).flatMap {
        case Some(duplicate) =>
          createForm(TalkForms.create.fillAndValidate(data).withError("slug", s"Slug already taken by talk: ${duplicate.title.value}"))
        case None =>
          db.createTalk(data, user.id).map { _ => Redirect(routes.TalkCtrl.detail(data.slug)) }
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
      speakers <- OptionT.liftF(db.getUsers(talkElt.speakers))
      proposals <- OptionT.liftF(db.getProposals(talkElt.id, Page.Params.defaults))
      h = header(talk)
      b = breadcrumb(user.name, talk -> talkElt.title)
    } yield Ok(html.detail(talkElt, speakers, proposals)(h, b))).value.map(_.getOrElse(talkNotFound(talk))).unsafeToFuture()
  }

  def edit(talk: Talk.Slug): Action[AnyContent] = Action.async { implicit req: Request[AnyContent] =>
    implicit val user: User = auth.authed()
    editForm(TalkForms.create, talk, isEmpty = true).unsafeToFuture()
  }

  def doEdit(talk: Talk.Slug): Action[AnyContent] = Action.async { implicit req: Request[AnyContent] =>
    implicit val user: User = auth.authed()
    TalkForms.create.bindFromRequest.fold(
      formWithErrors => editForm(formWithErrors, talk),
      data => db.getTalk(user.id, data.slug).flatMap {
        case Some(duplicate) if data.slug != talk =>
          editForm(TalkForms.create.fillAndValidate(data).withError("slug", s"Slug already taken by talk: ${duplicate.title.value}"), talk)
        case _ =>
          db.updateTalk(user.id, talk)(data).map { _ => Redirect(routes.TalkCtrl.detail(data.slug)) }
      }
    ).unsafeToFuture()
  }

  private def editForm(form: Form[Talk.Data], slug: Talk.Slug, isEmpty: Boolean = false)(implicit req: Request[AnyContent], user: User): IO[Result] = {
    db.getTalk(user.id, slug).map {
      case Some(talk) =>
        val h = header(talk.slug)
        val b = breadcrumb(user.name, talk.slug -> talk.title).add("Edit" -> routes.TalkCtrl.edit(talk.slug))
        val filledForm = if(isEmpty) form.fill(talk.data) else form
        Ok(html.edit(filledForm, talk)(h, b))
      case None =>
        talkNotFound(slug)
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
