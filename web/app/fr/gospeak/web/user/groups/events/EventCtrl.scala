package fr.gospeak.web.user.groups.events

import fr.gospeak.web.Values
import fr.gospeak.web.user.groups.GroupCtrl
import fr.gospeak.web.user.groups.events.EventCtrl._
import fr.gospeak.web.views.domain.{Breadcrumb, HeaderInfo, NavLink}
import play.api.mvc._

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class EventCtrl(cc: ControllerComponents) extends AbstractController(cc) {
  private val user = Values.user // logged user

  def list(group: String, search: Option[String], sortBy: Option[String], page: Option[Int], pageSize: Option[Int]): Action[AnyContent] = Action.async { implicit req: Request[AnyContent] =>
    for {
      groupIdOpt <- Values.getGroupId(group)
      groupOpt <- groupIdOpt.map(Values.getGroup(_, user.id)).getOrElse(Future.successful(None))
      events <- groupIdOpt.map(Values.getEvents).getOrElse(Future.successful(Seq()))
    } yield {
      (for {
        groupElt <- groupOpt
      } yield Ok(views.html.list(groupElt, events)(listHeader(group), listBreadcrumb(user.name, group -> groupElt.name.value)))).getOrElse(NotFound)
    }
  }

  def create(group: String): Action[AnyContent] = Action.async { implicit req: Request[AnyContent] =>
    for {
      groupIdOpt <- Values.getGroupId(group)
      groupOpt <- groupIdOpt.map(Values.getGroup(_, user.id)).getOrElse(Future.successful(None))
    } yield {
      (for {
        groupElt <- groupOpt
      } yield Ok(views.html.create(groupElt)(header(group), listBreadcrumb(user.name, group -> groupElt.name.value).add("New" -> routes.EventCtrl.create(group))))).getOrElse(NotFound)
    }
  }

  def doCreate(group: String): Action[AnyContent] = Action.async { implicit req: Request[AnyContent] =>
    ???
  }

  def detail(group: String, event: String): Action[AnyContent] = Action.async { implicit req: Request[AnyContent] =>
    for {
      groupIdOpt <- Values.getGroupId(group)
      eventIdOpt <- groupIdOpt.map(Values.getEventId(_, event)).getOrElse(Future.successful(None))
      groupOpt <- groupIdOpt.map(Values.getGroup(_, user.id)).getOrElse(Future.successful(None))
      eventOpt <- eventIdOpt.map(Values.getEvent).getOrElse(Future.successful(None))
    } yield {
      (for {
        groupElt <- groupOpt
        eventElt <- eventOpt
      } yield Ok(views.html.detail(groupElt, eventElt)(header(group), breadcrumb(user.name, group -> groupElt.name.value, event -> eventElt.name.value)))).getOrElse(NotFound)
    }
  }
}

object EventCtrl {
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
