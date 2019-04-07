package fr.gospeak.web.user.groups.events

import java.time.Instant

import cats.data.OptionT
import cats.effect.IO
import com.mohiva.play.silhouette.api.Silhouette
import com.mohiva.play.silhouette.api.actions.SecuredRequest
import fr.gospeak.core.domain._
import fr.gospeak.core.services._
import fr.gospeak.libs.scalautils.domain.Page
import fr.gospeak.web.auth.domain.CookieEnv
import fr.gospeak.web.domain.{Breadcrumb, HeaderInfo, NavLink}
import fr.gospeak.web.user.groups.GroupCtrl
import fr.gospeak.web.user.groups.events.EventCtrl._
import fr.gospeak.web.user.groups.routes.{GroupCtrl => GroupRoutes}
import fr.gospeak.web.utils.UICtrl
import play.api.data.Form
import play.api.mvc._

class EventCtrl(cc: ControllerComponents,
                silhouette: Silhouette[CookieEnv],
                userRepo: UserRepo,
                groupRepo: GroupRepo,
                cfpRepo: CfpRepo,
                eventRepo: EventRepo,
                proposalRepo: ProposalRepo) extends UICtrl(cc, silhouette) {

  import silhouette._

  def list(group: Group.Slug, params: Page.Params): Action[AnyContent] = SecuredAction.async { implicit req =>
    (for {
      groupElt <- OptionT(groupRepo.find(req.identity.user.id, group))
      events <- OptionT.liftF(eventRepo.list(groupElt.id, params))
      proposals <- OptionT.liftF(proposalRepo.list(events.items.flatMap(_.talks)))
      speakers <- OptionT.liftF(userRepo.list(proposals.flatMap(_.speakers.toList)))
      h = listHeader(group)
      b = listBreadcrumb(req.identity.user.name, groupElt)
    } yield Ok(html.list(groupElt, events, proposals, speakers)(h, b))).value.map(_.getOrElse(groupNotFound(group))).unsafeToFuture()
  }

  def create(group: Group.Slug): Action[AnyContent] = SecuredAction.async { implicit req =>
    createForm(group, EventForms.create).unsafeToFuture()
  }

  def doCreate(group: Group.Slug): Action[AnyContent] = SecuredAction.async { implicit req =>
    val now = Instant.now()
    EventForms.create.bindFromRequest.fold(
      formWithErrors => createForm(group, formWithErrors),
      data => (for {
        groupElt <- OptionT(groupRepo.find(req.identity.user.id, group))
        // TODO check if slug not already exist
        _ <- OptionT.liftF(eventRepo.create(groupElt.id, data.copy(venue = data.venue.map(_.trim)), req.identity.user.id, now))
      } yield Redirect(routes.EventCtrl.detail(group, data.slug))).value.map(_.getOrElse(groupNotFound(group)))
    ).unsafeToFuture()
  }

  private def createForm(group: Group.Slug, form: Form[Event.Data])(implicit req: SecuredRequest[CookieEnv, AnyContent]): IO[Result] = {
    (for {
      groupElt <- OptionT(groupRepo.find(req.identity.user.id, group))
      cfps <- OptionT.liftF(cfpRepo.listAll(groupElt.id))
      h = header(group)
      b = listBreadcrumb(req.identity.user.name, groupElt).add("New" -> routes.EventCtrl.create(group))
    } yield Ok(html.create(groupElt, form, cfps)(h, b))).value.map(_.getOrElse(groupNotFound(group)))
  }

  def detail(group: Group.Slug, event: Event.Slug, params: Page.Params): Action[AnyContent] = SecuredAction.async { implicit req =>
    (for {
      groupElt <- OptionT(groupRepo.find(req.identity.user.id, group))
      eventElt <- OptionT(eventRepo.find(groupElt.id, event))
      talks <- OptionT.liftF(proposalRepo.list(eventElt.talks))
      cfpOpt <- OptionT.liftF(cfpRepo.find(eventElt.id))
      proposals <- OptionT.liftF(cfpOpt.map(cfp => proposalRepo.list(cfp.id, Proposal.Status.Pending, params)).getOrElse(IO.pure(Page.empty[Proposal])))
      speakers <- OptionT.liftF(userRepo.list((proposals.items ++ talks).flatMap(_.speakers.toList).distinct))
      h = header(group)
      b = breadcrumb(req.identity.user.name, groupElt, eventElt)
    } yield Ok(html.detail(groupElt, eventElt, talks, cfpOpt, proposals, speakers)(h, b))).value.map(_.getOrElse(eventNotFound(group, event))).unsafeToFuture()
  }

  def edit(group: Group.Slug, event: Event.Slug): Action[AnyContent] = SecuredAction.async { implicit req =>
    editForm(group, event, EventForms.create).unsafeToFuture()
  }

  def doEdit(group: Group.Slug, event: Event.Slug): Action[AnyContent] = SecuredAction.async { implicit req =>
    val now = Instant.now()
    EventForms.create.bindFromRequest.fold(
      formWithErrors => editForm(group, event, formWithErrors),
      data => (for {
        groupElt <- OptionT(groupRepo.find(req.identity.user.id, group))
        eventOpt <- OptionT.liftF(eventRepo.find(groupElt.id, data.slug))
        res <- OptionT.liftF(eventOpt match {
          case Some(duplicate) if data.slug != event =>
            editForm(group, event, EventForms.create.fillAndValidate(data).withError("slug", s"Slug already taken by event: ${duplicate.name.value}"))
          case _ =>
            eventRepo.edit(groupElt.id, event)(data, req.identity.user.id, now).map { _ => Redirect(routes.EventCtrl.detail(group, data.slug)) }
        })
      } yield res).value.map(_.getOrElse(groupNotFound(group)))
    ).unsafeToFuture()
  }

  private def editForm(group: Group.Slug, event: Event.Slug, form: Form[Event.Data])(implicit req: SecuredRequest[CookieEnv, AnyContent]): IO[Result] = {
    (for {
      groupElt <- OptionT(groupRepo.find(req.identity.user.id, group))
      eventElt <- OptionT(eventRepo.find(groupElt.id, event))
      cfps <- OptionT.liftF(cfpRepo.listAll(groupElt.id))
      h = header(group)
      b = breadcrumb(req.identity.user.name, groupElt, eventElt).add("Edit" -> routes.EventCtrl.edit(group, event))
      filledForm = if (form.hasErrors) form else form.fill(eventElt.data)
    } yield Ok(html.edit(groupElt, eventElt, filledForm, cfps)(h, b))).value.map(_.getOrElse(eventNotFound(group, event)))
  }

  def addTalk(group: Group.Slug, event: Event.Slug, talk: Proposal.Id): Action[AnyContent] = SecuredAction.async { implicit req =>
    val now = Instant.now()
    (for {
      groupElt <- OptionT(groupRepo.find(req.identity.user.id, group))
      eventElt <- OptionT(eventRepo.find(groupElt.id, event))
      _ <- OptionT.liftF(eventRepo.editTalks(groupElt.id, event)(eventElt.add(talk).talks, req.identity.user.id, now))
      _ <- OptionT.liftF(proposalRepo.editStatus(talk)(Proposal.Status.Accepted, Some(eventElt.id)))
    } yield Redirect(routes.EventCtrl.detail(group, event))).value.map(_.getOrElse(eventNotFound(group, event))).unsafeToFuture()
  }

  def removeTalk(group: Group.Slug, event: Event.Slug, talk: Proposal.Id): Action[AnyContent] = SecuredAction.async { implicit req =>
    val now = Instant.now()
    (for {
      groupElt <- OptionT(groupRepo.find(req.identity.user.id, group))
      eventElt <- OptionT(eventRepo.find(groupElt.id, event))
      _ <- OptionT.liftF(eventRepo.editTalks(groupElt.id, event)(eventElt.remove(talk).talks, req.identity.user.id, now))
      _ <- OptionT.liftF(proposalRepo.editStatus(talk)(Proposal.Status.Pending, None))
    } yield Redirect(routes.EventCtrl.detail(group, event))).value.map(_.getOrElse(eventNotFound(group, event))).unsafeToFuture()
  }

  def moveTalk(group: Group.Slug, event: Event.Slug, talk: Proposal.Id, up: Boolean): Action[AnyContent] = SecuredAction.async { implicit req =>
    val now = Instant.now()
    (for {
      groupElt <- OptionT(groupRepo.find(req.identity.user.id, group))
      eventElt <- OptionT(eventRepo.find(groupElt.id, event))
      _ <- OptionT.liftF(eventRepo.editTalks(groupElt.id, event)(eventElt.move(talk, up).talks, req.identity.user.id, now))
    } yield Redirect(routes.EventCtrl.detail(group, event))).value.map(_.getOrElse(eventNotFound(group, event))).unsafeToFuture()
  }
}

object EventCtrl {
  def listHeader(group: Group.Slug)(implicit req: SecuredRequest[CookieEnv, AnyContent]): HeaderInfo =
    GroupCtrl.header(group)
      .copy(brand = NavLink("Gospeak", GroupRoutes.detail(group)))
      .activeFor(routes.EventCtrl.list(group))

  def listBreadcrumb(user: User.Name, group: Group): Breadcrumb =
    GroupCtrl.breadcrumb(user, group).add("Events" -> routes.EventCtrl.list(group.slug))

  def header(group: Group.Slug)(implicit req: SecuredRequest[CookieEnv, AnyContent]): HeaderInfo =
    listHeader(group)

  def breadcrumb(user: User.Name, group: Group, event: Event): Breadcrumb =
    listBreadcrumb(user, group).add(event.name.value -> routes.EventCtrl.detail(group.slug, event.slug))
}
