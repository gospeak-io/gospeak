package fr.gospeak.web.user.groups.events

import cats.data.OptionT
import cats.effect.IO
import fr.gospeak.core.domain.{Event, Group, User}
import fr.gospeak.core.services.GospeakDb
import fr.gospeak.libs.scalautils.domain.Page
import fr.gospeak.web.auth.AuthService
import fr.gospeak.web.domain.{Breadcrumb, HeaderInfo, NavLink}
import fr.gospeak.web.user.groups.GroupCtrl
import fr.gospeak.web.user.groups.events.EventCtrl._
import fr.gospeak.web.user.groups.routes.{GroupCtrl => GroupRoutes}
import fr.gospeak.web.utils.UICtrl
import play.api.data.Form
import play.api.mvc._

class EventCtrl(cc: ControllerComponents, db: GospeakDb, auth: AuthService) extends UICtrl(cc) {
  def list(group: Group.Slug, params: Page.Params): Action[AnyContent] = Action.async { implicit req: Request[AnyContent] =>
    implicit val user: User = auth.authed()
    (for {
      groupElt <- OptionT(db.getGroup(user.id, group))
      events <- OptionT.liftF(db.getEvents(groupElt.id, params))
      proposals <- OptionT.liftF(db.getProposals(events.items.flatMap(_.talks)))
      speakers <- OptionT.liftF(db.getUsers(proposals.flatMap(_.speakers.toList)))
      h = listHeader(group)
      b = listBreadcrumb(user.name, group -> groupElt.name)
    } yield Ok(html.list(groupElt, events, proposals, speakers)(h, b))).value.map(_.getOrElse(groupNotFound(group))).unsafeToFuture()
  }

  def create(group: Group.Slug): Action[AnyContent] = Action.async { implicit req: Request[AnyContent] =>
    implicit val user: User = auth.authed()
    createForm(group, EventForms.create).unsafeToFuture()
  }

  def doCreate(group: Group.Slug): Action[AnyContent] = Action.async { implicit req: Request[AnyContent] =>
    implicit val user: User = auth.authed()
    EventForms.create.bindFromRequest.fold(
      formWithErrors => createForm(group, formWithErrors),
      data => {
        (for {
          groupElt <- OptionT(db.getGroup(user.id, group))
          // TODO check if slug not already exist
          _ <- OptionT.liftF(db.createEvent(groupElt.id, data.slug, data.name, data.start, user.id))
        } yield Redirect(routes.EventCtrl.detail(group, data.slug))).value.map(_.getOrElse(groupNotFound(group)))
      }
    ).unsafeToFuture()
  }

  private def createForm(group: Group.Slug, form: Form[EventForms.Create])(implicit req: Request[AnyContent], user: User): IO[Result] = {
    (for {
      groupElt <- OptionT(db.getGroup(user.id, group))
      h = header(group)
      b = listBreadcrumb(user.name, group -> groupElt.name).add("New" -> routes.EventCtrl.create(group))
    } yield Ok(html.create(groupElt, form)(h, b))).value.map(_.getOrElse(groupNotFound(group)))
  }

  def detail(group: Group.Slug, event: Event.Slug): Action[AnyContent] = Action.async { implicit req: Request[AnyContent] =>
    implicit val user: User = auth.authed()
    (for {
      groupElt <- OptionT(db.getGroup(user.id, group))
      eventElt <- OptionT(db.getEvent(groupElt.id, event))
      h = header(group)
      b = breadcrumb(user.name, group -> groupElt.name, event -> eventElt.name)
    } yield Ok(html.detail(groupElt, eventElt)(h, b))).value.map(_.getOrElse(eventNotFound(group, event))).unsafeToFuture()
  }
}

object EventCtrl {
  def listHeader(group: Group.Slug): HeaderInfo =
    GroupCtrl.header(group)
      .copy(brand = NavLink("Gospeak", GroupRoutes.detail(group)))
      .activeFor(routes.EventCtrl.list(group))

  def listBreadcrumb(user: User.Name, group: (Group.Slug, Group.Name)): Breadcrumb =
    group match {
      case (groupSlug, _) => GroupCtrl.breadcrumb(user, group).add("Events" -> routes.EventCtrl.list(groupSlug))
    }

  def header(group: Group.Slug): HeaderInfo =
    listHeader(group)

  def breadcrumb(user: User.Name, group: (Group.Slug, Group.Name), event: (Event.Slug, Event.Name)): Breadcrumb =
    (group, event) match {
      case ((groupSlug, _), (eventSlug, eventName)) =>
        listBreadcrumb(user, group).add(eventName.value -> routes.EventCtrl.detail(groupSlug, eventSlug))
    }
}
