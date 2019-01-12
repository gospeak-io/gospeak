package fr.gospeak.web.user.talks

import fr.gospeak.web.user.UserCtrl
import fr.gospeak.web.user.talks.TalkCtrl._
import fr.gospeak.web.views.domain.{Breadcrumb, HeaderInfo, NavLink}
import play.api.mvc._

class TalkCtrl(cc: ControllerComponents) extends AbstractController(cc) {
  def list(): Action[AnyContent] = Action { implicit req: Request[AnyContent] =>
    Ok(views.html.list()(UserCtrl.header.activeFor(routes.TalkCtrl.list()), listBreadcrumb(UserCtrl.user)))
  }

  def detail(talk: String): Action[AnyContent] = Action { implicit req: Request[AnyContent] =>
    Ok(views.html.detail()(header(talk), breadcrumb(UserCtrl.user, talk -> talkName)))
  }
}

object TalkCtrl {
  val talkName = "Why FP"

  def listBreadcrumb(user: String): Breadcrumb =
    UserCtrl.breadcrumb(user).add("Talks" -> routes.TalkCtrl.list())

  def header(group: String): HeaderInfo =
    UserCtrl.header.copy(brand = NavLink("Gospeak", routes.TalkCtrl.detail(group)))
    .copy(brand = NavLink("Gospeak", fr.gospeak.web.user.routes.UserCtrl.index()))
      .activeFor(routes.TalkCtrl.list())

  def breadcrumb(user: String, talk: (String, String)): Breadcrumb =
    listBreadcrumb(user).add(talk._2 -> routes.TalkCtrl.detail(talk._1))
}
