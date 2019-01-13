package fr.gospeak.web.user.groups.events

import cats.data.OptionT
import cats.instances.future._
import fr.gospeak.core.domain.{Event, Group, User}
import fr.gospeak.web.Values
import fr.gospeak.web.domain.Page
import fr.gospeak.web.user.groups.GroupCtrl
import fr.gospeak.web.user.groups.events.EventCtrl._
import fr.gospeak.web.views.domain.{Breadcrumb, HeaderInfo, NavLink}
import play.api.mvc._

import scala.concurrent.ExecutionContext.Implicits.global

class EventCtrl(cc: ControllerComponents) extends AbstractController(cc) {
  private val user = Values.user // logged user

  def list(group: Group.Slug, params: Page.Params): Action[AnyContent] = Action.async { implicit req: Request[AnyContent] =>
    (for {
      groupId <- OptionT(Values.getGroupId(group))
      groupElt <- OptionT(Values.getGroup(groupId, user.id))
      events <- OptionT.liftF(Values.getEvents(groupId))
      eventPage = Page(events, params, Page.Total(45))
      h = listHeader(group)
      b = listBreadcrumb(user.name, group -> groupElt.name)
    } yield Ok(views.html.list(groupElt, eventPage)(h, b))).value.map(_.getOrElse(NotFound))
  }

  def create(group: Group.Slug): Action[AnyContent] = Action.async { implicit req: Request[AnyContent] =>
    (for {
      groupId <- OptionT(Values.getGroupId(group))
      groupElt <- OptionT(Values.getGroup(groupId, user.id))
      h = header(group)
      b = listBreadcrumb(user.name, group -> groupElt.name).add("New" -> routes.EventCtrl.create(group))
    } yield Ok(views.html.create(groupElt)(h, b))).value.map(_.getOrElse(NotFound))
  }

  def doCreate(group: Group.Slug): Action[AnyContent] = Action.async { implicit req: Request[AnyContent] =>
    ???
  }

  def detail(group: Group.Slug, event: Event.Slug): Action[AnyContent] = Action.async { implicit req: Request[AnyContent] =>
    (for {
      groupId <- OptionT(Values.getGroupId(group))
      eventId <- OptionT(Values.getEventId(groupId, event))
      groupElt <- OptionT(Values.getGroup(groupId, user.id))
      eventElt <- OptionT(Values.getEvent(eventId))
      h = header(group)
      b = breadcrumb(user.name, group -> groupElt.name, event -> eventElt.name)
    } yield Ok(views.html.detail(groupElt, eventElt)(h, b))).value.map(_.getOrElse(NotFound))
  }
}

object EventCtrl {
  def listHeader(group: Group.Slug): HeaderInfo =
    GroupCtrl.header(group)
      .copy(brand = NavLink("Gospeak", fr.gospeak.web.user.groups.routes.GroupCtrl.detail(group)))
      .activeFor(routes.EventCtrl.list(group))

  def listBreadcrumb(user: User.Name, group: (Group.Slug, Group.Name)): Breadcrumb =
    GroupCtrl.breadcrumb(user, group).add("Events" -> routes.EventCtrl.list(group._1))

  def header(group: Group.Slug): HeaderInfo =
    listHeader(group)

  def breadcrumb(user: User.Name, group: (Group.Slug, Group.Name), event: (Event.Slug, Event.Name)): Breadcrumb =
    listBreadcrumb(user, group).add(event._2.value -> routes.EventCtrl.detail(group._1, event._1))
}
