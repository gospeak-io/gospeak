package fr.gospeak.web.user.talks

import cats.data.OptionT
import cats.instances.future._
import fr.gospeak.core.domain.{Talk, User}
import fr.gospeak.web.Values
import fr.gospeak.web.user.UserCtrl
import fr.gospeak.web.user.talks.TalkCtrl._
import fr.gospeak.web.views.domain.{Breadcrumb, HeaderInfo, NavLink}
import play.api.mvc._

import scala.concurrent.ExecutionContext.Implicits.global

class TalkCtrl(cc: ControllerComponents) extends AbstractController(cc) {
  private val user = Values.user // logged user

  def list(): Action[AnyContent] = Action.async { implicit req: Request[AnyContent] =>
    for {
      talks <- Values.getTalks(user.id)
      h = UserCtrl.header.activeFor(routes.TalkCtrl.list())
      b = listBreadcrumb(user.name)
    } yield Ok(views.html.list(talks)(h, b))
  }

  def detail(talk: Talk.Slug): Action[AnyContent] = Action.async { implicit req: Request[AnyContent] =>
    (for {
      talkId <- OptionT(Values.getTalkId(talk))
      talkElt <- OptionT(Values.getTalk(talkId, user.id))
      proposals <- OptionT.liftF(Values.getProposals(talkId))
      h = header(talk)
      b = breadcrumb(user.name, talk -> talkElt.title)
    } yield Ok(views.html.detail(talkElt, proposals)(h, b))).value.map(_.getOrElse(NotFound))
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
