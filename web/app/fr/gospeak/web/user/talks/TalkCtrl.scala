package fr.gospeak.web.user.talks

import cats.data.OptionT
import cats.instances.future._
import fr.gospeak.core.domain.utils.Page
import fr.gospeak.core.domain.{Talk, User}
import fr.gospeak.core.services.GospeakDb
import fr.gospeak.web.domain.{Breadcrumb, HeaderInfo, NavLink}
import fr.gospeak.web.user.UserCtrl
import fr.gospeak.web.user.talks.TalkCtrl._
import play.api.data.Form
import play.api.i18n.I18nSupport
import play.api.mvc._

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class TalkCtrl(cc: ControllerComponents, db: GospeakDb) extends AbstractController(cc) with I18nSupport {
  private val user = db.getUser() // logged user

  def list(params: Page.Params): Action[AnyContent] = Action.async { implicit req: Request[AnyContent] =>
    for {
      talks <- db.getTalks(user.id)
      talkPage = Page(talks, params, Page.Total(45))
      h = listHeader
      b = listBreadcrumb(user.name)
    } yield Ok(html.list(talkPage)(h, b))
  }

  def create(): Action[AnyContent] = Action.async { implicit req: Request[AnyContent] =>
    createForm(TalkForms.create)
  }

  def doCreate(): Action[AnyContent] = Action.async { implicit req: Request[AnyContent] =>
    TalkForms.create.bindFromRequest.fold(
      formWithErrors => createForm(formWithErrors),
      data => {
        (for {
          // TODO check if slug not already exist
          _ <- OptionT.liftF(db.createTalk(data.slug, data.title, data.description, user.id))
        } yield Redirect(routes.TalkCtrl.detail(data.slug))).value.map(_.getOrElse(NotFound))
      }
    )
  }

  private def createForm(form: Form[TalkForms.Create])(implicit req: Request[AnyContent]): Future[Result] = {
    (for {
      _ <- OptionT.liftF(Future.successful())
      h = listHeader
      b = listBreadcrumb(user.name).add("New" -> routes.TalkCtrl.create())
    } yield Ok(html.create(form)(h, b))).value.map(_.getOrElse(NotFound))
  }

  def detail(talk: Talk.Slug): Action[AnyContent] = Action.async { implicit req: Request[AnyContent] =>
    (for {
      talkId <- OptionT(db.getTalkId(talk))
      talkElt <- OptionT(db.getTalk(talkId, user.id))
      proposals <- OptionT.liftF(db.getProposals(talkId))
      h = header(talk)
      b = breadcrumb(user.name, talk -> talkElt.title)
    } yield Ok(html.detail(talkElt, proposals)(h, b))).value.map(_.getOrElse(NotFound))
  }
}

object TalkCtrl {
  def listHeader: HeaderInfo =
    UserCtrl.header.activeFor(routes.TalkCtrl.list())

  def listBreadcrumb(user: User.Name): Breadcrumb =
    UserCtrl.breadcrumb(user).add("Talks" -> routes.TalkCtrl.list())

  def header(talk: Talk.Slug): HeaderInfo =
    UserCtrl.header.copy(brand = NavLink("Gospeak", routes.TalkCtrl.detail(talk)))
      .copy(brand = NavLink("Gospeak", fr.gospeak.web.user.routes.UserCtrl.index()))
      .activeFor(routes.TalkCtrl.list())

  def breadcrumb(user: User.Name, talk: (Talk.Slug, Talk.Title)): Breadcrumb =
    listBreadcrumb(user).add(talk._2.value -> routes.TalkCtrl.detail(talk._1))
}
