package fr.gospeak.web.pages.orga.events

import java.time.{Instant, LocalDateTime, ZoneOffset}

import cats.data.OptionT
import cats.effect.IO
import com.mohiva.play.silhouette.api.Silhouette
import com.mohiva.play.silhouette.api.actions.SecuredRequest
import fr.gospeak.core.ApplicationConf
import fr.gospeak.core.domain._
import fr.gospeak.core.services.meetup.MeetupSrv
import fr.gospeak.core.services.storage._
import fr.gospeak.infra.services.TemplateSrv
import fr.gospeak.libs.scalautils.Extensions._
import fr.gospeak.libs.scalautils.domain.{Html, Page}
import fr.gospeak.web.auth.domain.CookieEnv
import fr.gospeak.web.domain.{Breadcrumb, GospeakMessageBus, MessageBuilder}
import fr.gospeak.web.pages.orga.GroupCtrl
import fr.gospeak.web.pages.orga.events.EventCtrl._
import fr.gospeak.web.pages.orga.events.EventForms.PublishOptions
import fr.gospeak.web.services.EventSrv
import fr.gospeak.web.utils.UICtrl
import play.api.data.Form
import play.api.mvc._

class EventCtrl(cc: ControllerComponents,
                silhouette: Silhouette[CookieEnv],
                appConf: ApplicationConf,
                userRepo: OrgaUserRepo,
                groupRepo: OrgaGroupRepo,
                cfpRepo: OrgaCfpRepo,
                eventRepo: OrgaEventRepo,
                venueRepo: OrgaVenueRepo,
                proposalRepo: OrgaProposalRepo,
                groupSettingsRepo: GroupSettingsRepo,
                builder: MessageBuilder,
                templateSrv: TemplateSrv,
                eventSrv: EventSrv,
                meetupSrv: MeetupSrv,
                mb: GospeakMessageBus) extends UICtrl(cc, silhouette) {

  import silhouette._

  def list(group: Group.Slug, params: Page.Params): Action[AnyContent] = SecuredAction.async { implicit req =>
    (for {
      groupElt <- OptionT(groupRepo.find(user, group))
      events <- OptionT.liftF(eventRepo.list(groupElt.id, params))
      cfps <- OptionT.liftF(cfpRepo.list(events.items.flatMap(_.cfp)))
      venues <- OptionT.liftF(venueRepo.list(groupElt.id, events.items.flatMap(_.venue)))
      proposals <- OptionT.liftF(proposalRepo.list(events.items.flatMap(_.talks)))
      speakers <- OptionT.liftF(userRepo.list(proposals.flatMap(_.users)))
      b = listBreadcrumb(groupElt)
    } yield Ok(html.list(groupElt, events, cfps, venues, proposals, speakers)(b))).value.map(_.getOrElse(groupNotFound(group))).unsafeToFuture()
  }

  def create(group: Group.Slug): Action[AnyContent] = SecuredAction.async { implicit req =>
    createForm(group, EventForms.create).unsafeToFuture()
  }

  def doCreate(group: Group.Slug): Action[AnyContent] = SecuredAction.async { implicit req =>
    val now = Instant.now()
    EventForms.create.bindFromRequest.fold(
      formWithErrors => createForm(group, formWithErrors),
      data => (for {
        groupElt <- OptionT(groupRepo.find(user, group))
        // TODO check if slug not already exist
        eventElt <- OptionT.liftF(eventRepo.create(groupElt.id, data, by, now))
        _ <- OptionT.liftF(mb.publishEventCreated(groupElt, eventElt))
      } yield Redirect(routes.EventCtrl.detail(group, data.slug))).value.map(_.getOrElse(groupNotFound(group)))
    ).unsafeToFuture()
  }

  private def createForm(group: Group.Slug, form: Form[Event.Data])(implicit req: SecuredRequest[CookieEnv, AnyContent]): IO[Result] = {
    (for {
      groupElt <- OptionT(groupRepo.find(user, group))
      settings <- OptionT.liftF(groupSettingsRepo.find(groupElt.id))
      b = listBreadcrumb(groupElt).add("New" -> routes.EventCtrl.create(group))
      filledForm = if (form.hasErrors) form else form.bind(Map("description.value" -> settings.event.defaultDescription.value)).discardingErrors
    } yield Ok(html.create(groupElt, settings, filledForm)(b))).value.map(_.getOrElse(groupNotFound(group)))
  }

  def detail(group: Group.Slug, event: Event.Slug, params: Page.Params): Action[AnyContent] = SecuredAction.async { implicit req =>
    val customParams = params.defaultSize(40).defaultOrderBy(proposalRepo.fields.created)
    val nowLDT = LocalDateTime.now()
    (for {
      e <- OptionT(eventSrv.getFullEvent(group, event, user))
      settings <- OptionT.liftF(groupSettingsRepo.find(e.group.id))
      proposals <- OptionT.liftF(e.cfpOpt.map(cfp => proposalRepo.list(cfp.id, Proposal.Status.Pending, customParams)).getOrElse(IO.pure(Page.empty[Proposal])))
      speakers <- OptionT.liftF(userRepo.list(proposals.items.flatMap(_.users).distinct))
      desc = eventSrv.buildDescription(e, nowLDT)
      b = breadcrumb(e.group, e.event)
      res = Ok(html.detail(e.group, e.event, e.venueOpt, e.talks, desc, e.cfpOpt, proposals, e.speakers ++ speakers, settings.event, EventForms.attachCfp)(b))
    } yield res).value.map(_.getOrElse(eventNotFound(group, event))).unsafeToFuture()
  }

  def showTemplate(group: Group.Slug, event: Event.Slug, templateId: String): Action[AnyContent] = SecuredAction.async { implicit req =>
    val nowLDT = LocalDateTime.now()
    (for {
      e <- OptionT(eventSrv.getFullEvent(group, event, user))
      settings <- OptionT.liftF(groupSettingsRepo.find(e.group.id))
      data = builder.buildEventInfo(e, nowLDT)
      res = settings.event.templates.get(templateId)
        .flatMap(template => templateSrv.render(template, data).toOption)
        .map(text => Ok(html.showTemplate(Html(text))))
        .getOrElse(Redirect(routes.EventCtrl.detail(group, event)).flashing("error" -> s"Invalid template '$templateId'"))
    } yield res).value.map(_.getOrElse(eventNotFound(group, event))).unsafeToFuture()
  }

  def edit(group: Group.Slug, event: Event.Slug): Action[AnyContent] = SecuredAction.async { implicit req =>
    editForm(group, event, EventForms.create).unsafeToFuture()
  }

  def doEdit(group: Group.Slug, event: Event.Slug): Action[AnyContent] = SecuredAction.async { implicit req =>
    val now = Instant.now()
    EventForms.create.bindFromRequest.fold(
      formWithErrors => editForm(group, event, formWithErrors),
      data => (for {
        groupElt <- OptionT(groupRepo.find(user, group))
        eventOpt <- OptionT.liftF(eventRepo.find(groupElt.id, data.slug))
        res <- OptionT.liftF(eventOpt match {
          case Some(duplicate) if data.slug != event =>
            editForm(group, event, EventForms.create.fillAndValidate(data).withError("slug", s"Slug already taken by event: ${duplicate.name.value}"))
          case _ =>
            eventRepo.edit(groupElt.id, event)(data, by, now).map { _ => Redirect(routes.EventCtrl.detail(group, data.slug)) }
        })
      } yield res).value.map(_.getOrElse(groupNotFound(group)))
    ).unsafeToFuture()
  }

  private def editForm(group: Group.Slug, event: Event.Slug, form: Form[Event.Data])(implicit req: SecuredRequest[CookieEnv, AnyContent]): IO[Result] = {
    (for {
      groupElt <- OptionT(groupRepo.find(user, group))
      eventElt <- OptionT(eventRepo.find(groupElt.id, event))
      settings <- OptionT.liftF(groupSettingsRepo.find(groupElt.id))
      b = breadcrumb(groupElt, eventElt).add("Edit" -> routes.EventCtrl.edit(group, event))
      filledForm = if (form.hasErrors) form else form.fill(eventElt.data)
    } yield Ok(html.edit(groupElt, settings, eventElt, filledForm)(b))).value.map(_.getOrElse(eventNotFound(group, event)))
  }

  def attachCfp(group: Group.Slug, event: Event.Slug): Action[AnyContent] = SecuredAction.async { implicit req =>
    val now = Instant.now()
    EventForms.attachCfp.bindFromRequest.fold(
      formWithErrors => IO.pure(Redirect(routes.EventCtrl.detail(group, event)).flashing("error" -> s"Unable to attach CFP: ${formWithErrors.errors.map(_.format).mkString(", ")}")),
      cfp => (for {
        groupElt <- OptionT(groupRepo.find(user, group))
        cfpElt <- OptionT(cfpRepo.find(groupElt.id, cfp))
        _ <- OptionT.liftF(eventRepo.attachCfp(groupElt.id, event)(cfpElt.id, by, now))
      } yield Redirect(routes.EventCtrl.detail(group, event))).value.map(_.getOrElse(cfpNotFound(group, event, cfp)))
    ).unsafeToFuture()
  }

  def addToTalks(group: Group.Slug, event: Event.Slug, talk: Proposal.Id, params: Page.Params): Action[AnyContent] = SecuredAction.async { implicit req =>
    val now = Instant.now()
    val nowLDT = LocalDateTime.now()
    (for {
      groupElt <- OptionT(groupRepo.find(user, group))
      eventElt <- OptionT(eventRepo.find(groupElt.id, event))
      cfpElt <- OptionT(cfpRepo.find(eventElt.id))
      proposalElt <- OptionT(proposalRepo.find(cfpElt.slug, talk))
      _ <- OptionT.liftF(eventRepo.editTalks(groupElt.id, event)(eventElt.add(talk).talks, by, now))
      _ <- OptionT.liftF(proposalRepo.accept(cfpElt.slug, talk, eventElt.id, by, now))
      _ <- OptionT.liftF(mb.publishTalkAdded(groupElt, eventElt, cfpElt, proposalElt, nowLDT))
    } yield Redirect(routes.EventCtrl.detail(group, event, params))).value.map(_.getOrElse(eventNotFound(group, event))).unsafeToFuture()
  }

  def cancelTalk(group: Group.Slug, event: Event.Slug, talk: Proposal.Id, params: Page.Params): Action[AnyContent] = SecuredAction.async { implicit req =>
    val now = Instant.now()
    val nowLDT = LocalDateTime.now()
    (for {
      groupElt <- OptionT(groupRepo.find(user, group))
      eventElt <- OptionT(eventRepo.find(groupElt.id, event))
      cfpElt <- OptionT(cfpRepo.find(eventElt.id))
      proposalElt <- OptionT(proposalRepo.find(cfpElt.slug, talk))
      _ <- OptionT.liftF(eventRepo.editTalks(groupElt.id, event)(eventElt.remove(talk).talks, by, now))
      _ <- OptionT.liftF(proposalRepo.cancel(cfpElt.slug, talk, eventElt.id, by, now))
      _ <- OptionT.liftF(mb.publishTalkRemoved(groupElt, eventElt, cfpElt, proposalElt, nowLDT))
    } yield Redirect(routes.EventCtrl.detail(group, event, params))).value.map(_.getOrElse(eventNotFound(group, event))).unsafeToFuture()
  }

  def moveTalk(group: Group.Slug, event: Event.Slug, talk: Proposal.Id, up: Boolean, params: Page.Params): Action[AnyContent] = SecuredAction.async { implicit req =>
    val now = Instant.now()
    (for {
      groupElt <- OptionT(groupRepo.find(user, group))
      eventElt <- OptionT(eventRepo.find(groupElt.id, event))
      _ <- OptionT.liftF(eventRepo.editTalks(groupElt.id, event)(eventElt.move(talk, up).talks, by, now))
    } yield Redirect(routes.EventCtrl.detail(group, event, params))).value.map(_.getOrElse(eventNotFound(group, event))).unsafeToFuture()
  }

  def rejectProposal(group: Group.Slug, event: Event.Slug, talk: Proposal.Id, params: Page.Params): Action[AnyContent] = SecuredAction.async { implicit req =>
    val now = Instant.now()
    (for {
      groupElt <- OptionT(groupRepo.find(user, group))
      eventElt <- OptionT(eventRepo.find(groupElt.id, event))
      cfpElt <- OptionT(cfpRepo.find(eventElt.id))
      _ <- OptionT.liftF(proposalRepo.reject(cfpElt.slug, talk, by, now))
    } yield Redirect(routes.EventCtrl.detail(group, event, params))).value.map(_.getOrElse(eventNotFound(group, event))).unsafeToFuture()
  }

  def publish(group: Group.Slug, event: Event.Slug): Action[AnyContent] = SecuredAction.async { implicit req =>
    val nowLDT = LocalDateTime.now()
    publishForm(group, event, EventForms.publish.fill(PublishOptions.default), nowLDT).unsafeToFuture()
  }

  def doPublish(group: Group.Slug, event: Event.Slug): Action[AnyContent] = SecuredAction.async { implicit req =>
    val now = Instant.now()
    val nowLDT = LocalDateTime.now()
    EventForms.publish.bindFromRequest.fold(
      formWithErrors => publishForm(group, event, formWithErrors, nowLDT),
      data => (for {
        e <- OptionT(eventSrv.getFullEvent(group, event, user))
        description = eventSrv.buildDescription(e, nowLDT)
        settings <- OptionT.liftF(groupSettingsRepo.find(e.group.id))
        meetup <- OptionT.liftF((for {
          creds <- settings.accounts.meetup
          info <- data.meetup if info.publish
        } yield meetupSrv.publish(e.event, e.venueOpt, description.value, info.draft, appConf.aesKey, creds)).sequence)
        _ <- OptionT.liftF(meetup.map(_._1).filter(_ => e.event.refs.meetup.isEmpty)
          .map(ref => e.event.copy(refs = e.event.refs.copy(meetup = Some(ref))))
          .map(eventElt => eventRepo.edit(e.group.id, event)(eventElt.data, user, now)).sequence)
        _ <- OptionT.liftF(meetup.flatMap(m => m._2.flatMap(r => e.venueOpt.map(v => (r, v._2)))).filter { case (_, v) => v.refs.meetup.isEmpty }
          .map { case (ref, venue) => venue.copy(refs = venue.refs.copy(meetup = Some(ref))) }
          .map(venueElt => venueRepo.edit(e.group.id, venueElt.id)(venueElt.data, user, now)).sequence)
        _ <- OptionT.liftF(eventRepo.publish(e.group.id, event, user, now))
      } yield Redirect(routes.EventCtrl.detail(group, event))).value.map(_.getOrElse(eventNotFound(group, event)))).unsafeToFuture()
  }

  private def publishForm(group: Group.Slug, event: Event.Slug, form: Form[PublishOptions], nowLDT: LocalDateTime)(implicit req: SecuredRequest[CookieEnv, AnyContent]): IO[Result] = {
    (for {
      e <- OptionT(eventSrv.getFullEvent(group, event, user))
      description = eventSrv.buildDescription(e, nowLDT)
      settings <- OptionT.liftF(groupSettingsRepo.find(e.group.id))
      b = breadcrumb(e.group, e.event).add("Publish" -> routes.EventCtrl.publish(group, event))
      res = Ok(html.publish(e.group, e.event, description, form, settings)(b))
    } yield res).value.map(_.getOrElse(groupNotFound(group)))
  }
}

object EventCtrl {
  def listBreadcrumb(group: Group): Breadcrumb =
    GroupCtrl.breadcrumb(group).add("Events" -> routes.EventCtrl.list(group.slug))

  def breadcrumb(group: Group, event: Event): Breadcrumb =
    listBreadcrumb(group).add(event.name.value -> routes.EventCtrl.detail(group.slug, event.slug))
}
