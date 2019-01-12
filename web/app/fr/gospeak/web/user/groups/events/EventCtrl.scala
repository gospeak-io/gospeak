package fr.gospeak.web.user.groups.events

import fr.gospeak.web.user.UserCtrl
import fr.gospeak.web.user.groups.GroupCtrl
import fr.gospeak.web.user.groups.events.EventCtrl._
import fr.gospeak.web.views.domain.{Breadcrumb, HeaderInfo, NavLink}
import play.api.mvc._

class EventCtrl(cc: ControllerComponents) extends AbstractController(cc) {
  def list(group: String, search: Option[String], sortBy: Option[String], page: Option[Int], pageSize: Option[Int]): Action[AnyContent] = Action { implicit req: Request[AnyContent] =>
    Ok(views.html.list()(listHeader(group), listBreadcrumb(UserCtrl.user, group -> GroupCtrl.groupName)))
  }

  def create(group: String): Action[AnyContent] = Action { implicit req: Request[AnyContent] =>
    Ok(views.html.create()(header(group), listBreadcrumb(UserCtrl.user, group -> GroupCtrl.groupName).add("New" -> routes.EventCtrl.create(group))))
  }

  def detail(group: String, event: String): Action[AnyContent] = Action { implicit req: Request[AnyContent] =>
    Ok(views.html.detail()(header(group), breadcrumb(UserCtrl.user, group -> GroupCtrl.groupName, event -> eventName)))
  }
}

object EventCtrl {
  val eventName = "HumanTalks Paris Mars 2019"

  def listHeader(group: String): HeaderInfo =
    GroupCtrl.header(group)
      .copy(brand = NavLink("Gospeak", fr.gospeak.web.user.groups.routes.GroupCtrl.detail(group)))
      .activeFor(routes.EventCtrl.list(group))

  def listBreadcrumb(user: String, group: (String, String)): Breadcrumb =
    GroupCtrl.breadcrumb(user, group).add("Events" -> routes.EventCtrl.list(group._1))

  def header(group: String): HeaderInfo =
    listHeader(group)

  def breadcrumb(user: String, group: (String, String), event: (String, String)): Breadcrumb =
    listBreadcrumb(user, group).add(event._2 -> routes.EventCtrl.detail(group._1, event._1))
}
