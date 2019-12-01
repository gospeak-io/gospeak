package fr.gospeak.web.pages.orga.events

import cats.data.OptionT
import cats.effect.IO
import com.mohiva.play.silhouette.api.Silhouette
import fr.gospeak.core.ApplicationConf
import fr.gospeak.core.domain._
import fr.gospeak.core.domain.utils.OrgaCtx
import fr.gospeak.core.services.TemplateSrv
import fr.gospeak.core.services.email.EmailSrv
import fr.gospeak.core.services.meetup.MeetupSrv
import fr.gospeak.core.services.storage._
import fr.gospeak.libs.scalautils.Extensions._
import fr.gospeak.libs.scalautils.domain.{Done, Html, Page}
import fr.gospeak.web.auth.domain.CookieEnv
import fr.gospeak.web.domain.{Breadcrumb, GospeakMessageBus, MessageBuilder}
import fr.gospeak.web.emails.Emails
import fr.gospeak.web.pages.orga.GroupCtrl
import fr.gospeak.web.pages.orga.events.EventCtrl._
import fr.gospeak.web.pages.orga.events.EventForms.PublishOptions
import fr.gospeak.web.services.EventSrv
import fr.gospeak.web.utils.{OrgaReq, UICtrl}
import play.api.data.Form
import play.api.mvc._

import scala.util.control.NonFatal

class EventCtrl(cc: ControllerComponents,
                silhouette: Silhouette[CookieEnv],
                env: ApplicationConf.Env,
                appConf: ApplicationConf,
                userRepo: OrgaUserRepo,
                val groupRepo: OrgaGroupRepo,
                cfpRepo: OrgaCfpRepo,
                eventRepo: OrgaEventRepo,
                venueRepo: OrgaVenueRepo,
                proposalRepo: OrgaProposalRepo,
                groupSettingsRepo: GroupSettingsRepo,
                builder: MessageBuilder,
                templateSrv: TemplateSrv,
                eventSrv: EventSrv,
                meetupSrv: MeetupSrv,
                emailSrv: EmailSrv,
                mb: GospeakMessageBus) extends UICtrl(cc, silhouette, env) with UICtrl.OrgaAction {
  def list(group: Group.Slug, params: Page.Params): Action[AnyContent] = OrgaAction(group)(implicit req => implicit ctx => {
    for {
      events <- eventRepo.list(params)
      cfps <- cfpRepo.list(events.items.flatMap(_.cfp))
      venues <- venueRepo.listFull(events.items.flatMap(_.venue))
      proposals <- proposalRepo.list(events.items.flatMap(_.talks))
      speakers <- userRepo.list(proposals.flatMap(_.users))
    } yield Ok(html.list(events, cfps, venues, proposals, speakers)(listBreadcrumb))
  })

  def create(group: Group.Slug): Action[AnyContent] = OrgaAction(group)(implicit req => implicit ctx => {
    createView(group, EventForms.create)
  })

  def doCreate(group: Group.Slug): Action[AnyContent] = OrgaAction(group)(implicit req => implicit ctx => {
    EventForms.create.bindFromRequest.fold(
      formWithErrors => createView(group, formWithErrors),
      data => (for {
        // TODO check if slug not already exist
        eventElt <- OptionT.liftF(eventRepo.create(data))
        _ <- OptionT.liftF(mb.publishEventCreated(eventElt))
      } yield Redirect(routes.EventCtrl.detail(group, data.slug))).value.map(_.getOrElse(groupNotFound(group)))
    )
  })

  private def createView(group: Group.Slug, form: Form[Event.Data])(implicit req: OrgaReq[AnyContent], ctx: OrgaCtx): IO[Result] = {
    for {
      meetupAccount <- groupSettingsRepo.findMeetup
      eventDescription <- groupSettingsRepo.findEventDescription
      filledForm = if (form.hasErrors) form else form.bind(Map("description.value" -> eventDescription.value)).discardingErrors
      b = listBreadcrumb.add("New" -> routes.EventCtrl.create(group))
    } yield Ok(html.create(meetupAccount.isDefined, filledForm)(b))
  }

  def detail(group: Group.Slug, event: Event.Slug, params: Page.Params): Action[AnyContent] = OrgaAction(group)(implicit req => implicit ctx => {
    val customParams = params.defaultSize(40).defaultOrderBy(proposalRepo.fields.created)
    (for {
      e <- OptionT(eventSrv.getFullEvent(event))
      eventTemplates <- OptionT.liftF(groupSettingsRepo.findEventTemplates)
      proposals <- OptionT.liftF(e.cfpOpt.map(cfp => proposalRepo.listFull(cfp.id, Proposal.Status.Pending, customParams)).getOrElse(IO.pure(Page.empty[Proposal.Full])))
      speakers <- OptionT.liftF(userRepo.list(proposals.items.flatMap(_.users).distinct))
      userRatings <- OptionT.liftF(proposalRepo.listRatings(proposals.items.map(_.id)))
      desc = eventSrv.buildDescription(e)
      b = breadcrumb(e.event)
      res = Ok(html.detail(e.event, e.venueOpt, e.talks, desc, e.cfpOpt, proposals, e.speakers ++ speakers, userRatings, eventTemplates, EventForms.cfp, EventForms.notes.fill(e.event.orgaNotes.text))(b))
    } yield res).value.map(_.getOrElse(eventNotFound(group, event)))
  })

  def showTemplate(group: Group.Slug, event: Event.Slug, templateId: String): Action[AnyContent] = OrgaAction(group)(implicit req => implicit ctx => {
    (for {
      e <- OptionT(eventSrv.getFullEvent(event))
      eventTemplates <- OptionT.liftF(groupSettingsRepo.findEventTemplates)
      data = builder.buildEventInfo(e)
      res = eventTemplates.get(templateId)
        .flatMap(template => templateSrv.render(template, data).toOption)
        .map(text => Ok(html.showTemplate(Html(text))))
        .getOrElse(Redirect(routes.EventCtrl.detail(group, event)).flashing("error" -> s"Invalid template '$templateId'"))
    } yield res).value.map(_.getOrElse(eventNotFound(group, event)))
  })

  def edit(group: Group.Slug, event: Event.Slug): Action[AnyContent] = OrgaAction(group)(implicit req => implicit ctx => {
    editView(group, event, EventForms.create)
  })

  def doEdit(group: Group.Slug, event: Event.Slug): Action[AnyContent] = OrgaAction(group)(implicit req => implicit ctx => {
    EventForms.create.bindFromRequest.fold(
      formWithErrors => editView(group, event, formWithErrors),
      data => for {
        eventOpt <- eventRepo.find(data.slug)
        res <- eventOpt match {
          case Some(duplicate) if data.slug != event =>
            editView(group, event, EventForms.create.fillAndValidate(data).withError("slug", s"Slug already taken by event: ${duplicate.name.value}"))
          case _ =>
            eventRepo.edit(event, data).map { _ => Redirect(routes.EventCtrl.detail(group, data.slug)) }
        }
      } yield res
    )
  })

  private def editView(group: Group.Slug, event: Event.Slug, form: Form[Event.Data])(implicit req: OrgaReq[AnyContent], ctx: OrgaCtx): IO[Result] = {
    (for {
      eventElt <- OptionT(eventRepo.find(event))
      meetupAccount <- OptionT.liftF(groupSettingsRepo.findMeetup)
      b = breadcrumb(eventElt).add("Edit" -> routes.EventCtrl.edit(group, event))
      filledForm = if (form.hasErrors) form else form.fill(eventElt.data)
    } yield Ok(html.edit(meetupAccount.isDefined, eventElt, filledForm)(b))).value.map(_.getOrElse(eventNotFound(group, event)))
  }

  def doEditNotes(group: Group.Slug, event: Event.Slug): Action[AnyContent] = OrgaAction(group)(implicit req => implicit ctx => {
    EventForms.notes.bindFromRequest.fold(
      formWithErrors => IO.pure(Redirect(routes.EventCtrl.detail(group, event)).flashing("error" -> s"Unable to edit notes: ${req.formatErrors(formWithErrors)}")),
      notes => eventRepo.editNotes(event, notes).map(_ => Redirect(routes.EventCtrl.detail(group, event)))
    )
  })

  def attachCfp(group: Group.Slug, event: Event.Slug): Action[AnyContent] = OrgaAction(group)(implicit req => implicit ctx => {
    EventForms.cfp.bindFromRequest.fold(
      formWithErrors => IO.pure(Redirect(routes.EventCtrl.detail(group, event)).flashing("error" -> s"Unable to attach CFP: ${req.formatErrors(formWithErrors)}")),
      cfp => eventRepo.attachCfp(event, cfp).map(_ => Redirect(routes.EventCtrl.detail(group, event)))
    )
  })

  def addToTalks(group: Group.Slug, event: Event.Slug, talk: Proposal.Id, params: Page.Params): Action[AnyContent] = OrgaAction(group)(implicit req => implicit ctx => {
    (for {
      eventElt <- OptionT(eventRepo.find(event))
      cfpElt <- OptionT(cfpRepo.find(eventElt.id))
      proposalElt <- OptionT(proposalRepo.find(cfpElt.slug, talk))
      _ <- OptionT.liftF(eventRepo.editTalks(event, eventElt.add(talk).talks))
      _ <- OptionT.liftF(proposalRepo.accept(cfpElt.slug, talk, eventElt.id))
      _ <- OptionT.liftF(mb.publishTalkAdded(eventElt, cfpElt, proposalElt))
    } yield Redirect(routes.EventCtrl.detail(group, event, params))).value.map(_.getOrElse(eventNotFound(group, event)))
  })

  def cancelTalk(group: Group.Slug, event: Event.Slug, talk: Proposal.Id, params: Page.Params): Action[AnyContent] = OrgaAction(group)(implicit req => implicit ctx => {
    (for {
      eventElt <- OptionT(eventRepo.find(event))
      cfpElt <- OptionT(cfpRepo.find(eventElt.id))
      proposalElt <- OptionT(proposalRepo.find(cfpElt.slug, talk))
      _ <- OptionT.liftF(eventRepo.editTalks(event, eventElt.remove(talk).talks))
      _ <- OptionT.liftF(proposalRepo.cancel(cfpElt.slug, talk, eventElt.id))
      _ <- OptionT.liftF(mb.publishTalkRemoved(eventElt, cfpElt, proposalElt))
    } yield Redirect(routes.EventCtrl.detail(group, event, params))).value.map(_.getOrElse(eventNotFound(group, event)))
  })

  def moveTalk(group: Group.Slug, event: Event.Slug, talk: Proposal.Id, up: Boolean, params: Page.Params): Action[AnyContent] = OrgaAction(group)(implicit req => implicit ctx => {
    (for {
      eventElt <- OptionT(eventRepo.find(event))
      _ <- OptionT.liftF(eventRepo.editTalks(event, eventElt.move(talk, up).talks))
    } yield Redirect(routes.EventCtrl.detail(group, event, params))).value.map(_.getOrElse(eventNotFound(group, event)))
  })

  def rejectProposal(group: Group.Slug, event: Event.Slug, talk: Proposal.Id, params: Page.Params): Action[AnyContent] = OrgaAction(group)(implicit req => implicit ctx => {
    (for {
      eventElt <- OptionT(eventRepo.find(event))
      cfpElt <- OptionT(cfpRepo.find(eventElt.id))
      _ <- OptionT.liftF(proposalRepo.reject(cfpElt.slug, talk))
    } yield Redirect(routes.EventCtrl.detail(group, event, params))).value.map(_.getOrElse(eventNotFound(group, event)))
  })

  def publish(group: Group.Slug, event: Event.Slug): Action[AnyContent] = OrgaAction(group)(implicit req => implicit ctx => {
    publishView(group, event, EventForms.publish.fill(PublishOptions.default))
  })

  def doPublish(group: Group.Slug, event: Event.Slug): Action[AnyContent] = OrgaAction(group)(implicit req => implicit ctx => {
    EventForms.publish.bindFromRequest.fold(
      formWithErrors => publishView(group, event, formWithErrors),
      data => (for {
        e <- OptionT(eventSrv.getFullEvent(event))
        description = eventSrv.buildDescription(e)
        meetupAccount <- OptionT.liftF(groupSettingsRepo.findMeetup)
        meetup <- OptionT.liftF((for {
          creds <- meetupAccount
          info <- data.meetup if info.publish
        } yield meetupSrv.publish(e.event, e.venueOpt, description, info.draft, appConf.aesKey, creds)).sequence)
        _ <- OptionT.liftF(meetup.map(_._1).filter(_ => e.event.refs.meetup.isEmpty)
          .map(ref => e.event.copy(refs = e.event.refs.copy(meetup = Some(ref))))
          .map(eventElt => eventRepo.edit(event, eventElt.data)).sequence)
        _ <- OptionT.liftF(meetup.flatMap(m => m._2.flatMap(r => e.venueOpt.map(v => (r, v)))).filter { case (_, v) => v.refs.meetup.isEmpty }
          .map { case (ref, venue) => venueRepo.edit(venue.id, venue.data.copy(refs = venue.refs.copy(meetup = Some(ref)))) }.sequence)
        _ <- OptionT.liftF(eventRepo.publish(event))
        _ <- if (data.notifyMembers) {
          OptionT.liftF(groupRepo.listMembers
            .flatMap(members => members.map(m => emailSrv.send(Emails.eventPublished(e.event, e.venueOpt, m))).sequence))
        } else {
          OptionT.liftF(IO.pure(Seq.empty[Done]))
        }
        _ <- OptionT.liftF(mb.publishEventPublished(e.event))
      } yield Redirect(routes.EventCtrl.detail(group, event))).value.map(_.getOrElse(eventNotFound(group, event))))
      .recover {
        case NonFatal(e) => Redirect(routes.EventCtrl.detail(group, event)).flashing("error" -> s"An error happened: ${e.getMessage}")
      }
  })

  private def publishView(group: Group.Slug, event: Event.Slug, form: Form[PublishOptions])(implicit req: OrgaReq[AnyContent], ctx: OrgaCtx): IO[Result] = {
    (for {
      e <- OptionT(eventSrv.getFullEvent(event))
      description = eventSrv.buildDescription(e)
      meetupAccount <- OptionT.liftF(groupSettingsRepo.findMeetup)
      b = breadcrumb(e.event).add("Publish" -> routes.EventCtrl.publish(group, event))
      res = Ok(html.publish(e.event, description, form, meetupAccount.isDefined)(b))
    } yield res).value.map(_.getOrElse(groupNotFound(group)))
  }


  def contactRsvps(group: Group.Slug, event: Event.Slug): Action[AnyContent] = OrgaAction(group)(implicit req => implicit ctx => {
    contactRsvpsView(group, event, EventForms.contactAttendees)
  })

  def doContactRsvps(group: Group.Slug, event: Event.Slug): Action[AnyContent] = OrgaAction(group)(implicit req => implicit ctx => {
    EventForms.contactAttendees.bindFromRequest.fold(
      formWithErrors => contactRsvpsView(group, event, formWithErrors),
      data => (for {
        eventElt <- OptionT(eventRepo.find(event))
        sender <- OptionT(IO.pure(req.senders.find(_.address == data.from)))
        rsvps <- OptionT.liftF(eventRepo.listRsvps(eventElt.id, data.to.answers))
        _ <- OptionT.liftF(rsvps.map(r => emailSrv.send(Emails.eventMessage(eventElt, sender, data.subject, data.content, r))).sequence)
        next = Redirect(routes.EventCtrl.detail(group, event))
      } yield next.flashing("success" -> "Message sent to event attendees")).value.map(_.getOrElse(groupNotFound(group)))
    ).recover {
      case NonFatal(e) => Redirect(routes.EventCtrl.detail(group, event)).flashing("error" -> s"An error happened: ${e.getMessage}")
    }
  })

  private def contactRsvpsView(group: Group.Slug, event: Event.Slug, form: Form[EventForms.ContactAttendees])(implicit req: OrgaReq[AnyContent], ctx: OrgaCtx): IO[Result] = {
    (for {
      eventElt <- OptionT(eventRepo.find(event))
      b = breadcrumb(eventElt).add("Contact attendees" -> routes.EventCtrl.contactRsvps(group, event))
    } yield Ok(html.contactRsvps(eventElt, req.senders, form)(b))).value.map(_.getOrElse(groupNotFound(group)))
  }
}

object EventCtrl {
  def listBreadcrumb(implicit req: OrgaReq[AnyContent]): Breadcrumb =
    GroupCtrl.breadcrumb.add("Events" -> routes.EventCtrl.list(req.group.slug))

  def breadcrumb(event: Event)(implicit req: OrgaReq[AnyContent]): Breadcrumb =
    listBreadcrumb.add(event.name.value -> routes.EventCtrl.detail(req.group.slug, event.slug))
}
