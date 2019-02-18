package fr.gospeak.web.user.groups.events

import java.time.Instant

import cats.data.OptionT
import cats.effect.IO
import fr.gospeak.core.domain.{Event, Group, Proposal, User}
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
    val now = Instant.now()
    EventForms.create.bindFromRequest.fold(
      formWithErrors => createForm(group, formWithErrors),
      data => (for {
        groupElt <- OptionT(db.getGroup(user.id, group))
        // TODO check if slug not already exist
        _ <- OptionT.liftF(db.createEvent(groupElt.id, data.copy(venue = data.venue.map(_.trim)), user.id, now))
      } yield Redirect(routes.EventCtrl.detail(group, data.slug))).value.map(_.getOrElse(groupNotFound(group)))
    ).unsafeToFuture()
  }

  private def createForm(group: Group.Slug, form: Form[Event.Data])(implicit req: Request[AnyContent], user: User): IO[Result] = {
    (for {
      groupElt <- OptionT(db.getGroup(user.id, group))
      h = header(group)
      b = listBreadcrumb(user.name, group -> groupElt.name).add("New" -> routes.EventCtrl.create(group))
    } yield Ok(html.create(groupElt, form)(h, b))).value.map(_.getOrElse(groupNotFound(group)))
  }

  def detail(group: Group.Slug, event: Event.Slug, params: Page.Params): Action[AnyContent] = Action.async { implicit req: Request[AnyContent] =>
    implicit val user: User = auth.authed()
    (for {
      groupElt <- OptionT(db.getGroup(user.id, group))
      eventElt <- OptionT(db.getEvent(groupElt.id, event))
      talks <- OptionT.liftF(db.getProposals(eventElt.talks))
      cfpOpt <- OptionT.liftF(db.getCfp(groupElt.id))
      proposals <- OptionT.liftF(cfpOpt.map(cfp => db.getProposals(cfp.id, Proposal.Status.Pending, params)).getOrElse(IO.pure(Page.empty[Proposal])))
      speakers <- OptionT.liftF(db.getUsers((proposals.items ++ talks).flatMap(_.speakers.toList).distinct))
      h = header(group)
      b = breadcrumb(user.name, group -> groupElt.name, event -> eventElt.name)
    } yield Ok(html.detail(groupElt, eventElt, talks, cfpOpt, proposals, speakers)(h, b))).value.map(_.getOrElse(eventNotFound(group, event))).unsafeToFuture()
  }

  def edit(group: Group.Slug, event: Event.Slug): Action[AnyContent] = Action.async { implicit req: Request[AnyContent] =>
    implicit val user: User = auth.authed()
    editForm(group, event, EventForms.create).unsafeToFuture()
  }

  def doEdit(group: Group.Slug, event: Event.Slug): Action[AnyContent] = Action.async { implicit req: Request[AnyContent] =>
    implicit val user: User = auth.authed()
    val now = Instant.now()
    EventForms.create.bindFromRequest.fold(
      formWithErrors => editForm(group, event, formWithErrors),
      data => (for {
        groupElt <- OptionT(db.getGroup(user.id, group))
        eventOpt <- OptionT.liftF(db.getEvent(groupElt.id, data.slug))
        res <- OptionT.liftF(eventOpt match {
          case Some(duplicate) if data.slug != event =>
            editForm(group, event, EventForms.create.fillAndValidate(data).withError("slug", s"Slug already taken by event: ${duplicate.name.value}"))
          case _ =>
            db.updateEvent(groupElt.id, event)(data, user.id, now).map { _ => Redirect(routes.EventCtrl.detail(group, data.slug)) }
        })
      } yield res).value.map(_.getOrElse(groupNotFound(group)))
    ).unsafeToFuture()
  }

  private def editForm(group: Group.Slug, event: Event.Slug, form: Form[Event.Data])(implicit req: Request[AnyContent], user: User): IO[Result] = {
    (for {
      groupElt <- OptionT(db.getGroup(user.id, group))
      eventElt <- OptionT(db.getEvent(groupElt.id, event))
      h = header(group)
      b = breadcrumb(user.name, group -> groupElt.name, event -> eventElt.name).add("Edit" -> routes.EventCtrl.edit(group, event))
      filledForm = if (form.hasErrors) form else form.fill(eventElt.data)
    } yield Ok(html.edit(groupElt, eventElt, filledForm)(h, b))).value.map(_.getOrElse(eventNotFound(group, event)))
  }

  def addTalk(group: Group.Slug, event: Event.Slug, talk: Proposal.Id): Action[AnyContent] = Action.async { implicit req: Request[AnyContent] =>
    implicit val user: User = auth.authed()
    val now = Instant.now()
    (for {
      groupElt <- OptionT(db.getGroup(user.id, group))
      eventElt <- OptionT(db.getEvent(groupElt.id, event))
      _ <- OptionT.liftF(db.updateEventTalks(groupElt.id, event)(eventElt.add(talk).talks, user.id, now))
      _ <- OptionT.liftF(db.updateProposalStatus(talk)(Proposal.Status.Accepted, Some(eventElt.id)))
    } yield Redirect(routes.EventCtrl.detail(group, event))).value.map(_.getOrElse(eventNotFound(group, event))).unsafeToFuture()
  }

  def removeTalk(group: Group.Slug, event: Event.Slug, talk: Proposal.Id): Action[AnyContent] = Action.async { implicit req: Request[AnyContent] =>
    implicit val user: User = auth.authed()
    val now = Instant.now()
    (for {
      groupElt <- OptionT(db.getGroup(user.id, group))
      eventElt <- OptionT(db.getEvent(groupElt.id, event))
      _ <- OptionT.liftF(db.updateEventTalks(groupElt.id, event)(eventElt.remove(talk).talks, user.id, now))
      _ <- OptionT.liftF(db.updateProposalStatus(talk)(Proposal.Status.Pending, None))
    } yield Redirect(routes.EventCtrl.detail(group, event))).value.map(_.getOrElse(eventNotFound(group, event))).unsafeToFuture()
  }

  def moveTalk(group: Group.Slug, event: Event.Slug, talk: Proposal.Id, up: Boolean): Action[AnyContent] = Action.async { implicit req: Request[AnyContent] =>
    implicit val user: User = auth.authed()
    val now = Instant.now()
    (for {
      groupElt <- OptionT(db.getGroup(user.id, group))
      eventElt <- OptionT(db.getEvent(groupElt.id, event))
      _ <- OptionT.liftF(db.updateEventTalks(groupElt.id, event)(eventElt.move(talk, up).talks, user.id, now))
    } yield Redirect(routes.EventCtrl.detail(group, event))).value.map(_.getOrElse(eventNotFound(group, event))).unsafeToFuture()
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
