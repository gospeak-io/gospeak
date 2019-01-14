package fr.gospeak.web.user.talks

import cats.data.OptionT
import cats.instances.future._
import fr.gospeak.core.domain.utils.Page
import fr.gospeak.core.domain.{Talk, User}
import fr.gospeak.core.services.GospeakDb
import fr.gospeak.web.user.UserCtrl
import fr.gospeak.web.user.talks.TalkCtrl._
import fr.gospeak.web.domain.{Breadcrumb, HeaderInfo, NavLink}
import play.api.mvc._

import scala.concurrent.ExecutionContext.Implicits.global

class TalkCtrl(cc: ControllerComponents, db: GospeakDb) extends AbstractController(cc) {
  private val user = db.getUser() // logged user

  def list(params: Page.Params): Action[AnyContent] = Action.async { implicit req: Request[AnyContent] =>
    for {
      talks <- db.getTalks(user.id)
      talkPage = Page(talks, params, Page.Total(45))
      h = UserCtrl.header.activeFor(routes.TalkCtrl.list())
      b = listBreadcrumb(user.name)
    } yield Ok(html.list(talkPage)(h, b))
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
  def listBreadcrumb(user: User.Name): Breadcrumb =
    UserCtrl.breadcrumb(user).add("Talks" -> routes.TalkCtrl.list())

  def header(talk: Talk.Slug): HeaderInfo =
    UserCtrl.header.copy(brand = NavLink("Gospeak", routes.TalkCtrl.detail(talk)))
      .copy(brand = NavLink("Gospeak", fr.gospeak.web.user.routes.UserCtrl.index()))
      .activeFor(routes.TalkCtrl.list())

  def breadcrumb(user: User.Name, talk: (Talk.Slug, Talk.Title)): Breadcrumb =
    listBreadcrumb(user).add(talk._2.value -> routes.TalkCtrl.detail(talk._1))
}
