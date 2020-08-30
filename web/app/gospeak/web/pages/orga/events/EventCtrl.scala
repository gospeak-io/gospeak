package gospeak.web.pages.orga.events

import cats.data.OptionT
import cats.effect.IO
import com.mohiva.play.silhouette.api.Silhouette
import gospeak.core.domain._
import gospeak.core.domain.messages.Message
import gospeak.core.domain.utils.OrgaCtx
import gospeak.core.services.email.EmailSrv
import gospeak.core.services.meetup.MeetupSrv
import gospeak.core.services.storage._
import gospeak.libs.scala.Extensions._
import gospeak.libs.scala.MessageBus
import gospeak.libs.scala.domain.{Done, Markdown, Page}
import gospeak.web.AppConf
import gospeak.web.auth.domain.CookieEnv
import gospeak.web.domain.Breadcrumb
import gospeak.web.emails.Emails
import gospeak.web.pages.orga.GroupCtrl
import gospeak.web.pages.orga.events.EventCtrl._
import gospeak.web.services.MessageSrv
import gospeak.web.services.MessageSrv._
import gospeak.web.utils.GsForms.PublishOptions
import gospeak.web.utils.{GsForms, OrgaReq, UICtrl}
import play.api.data.Form
import play.api.mvc._

import scala.util.control.NonFatal

class EventCtrl(cc: ControllerComponents,
                silhouette: Silhouette[CookieEnv],
                conf: AppConf,
                userRepo: OrgaUserRepo,
                val groupRepo: OrgaGroupRepo,
                cfpRepo: OrgaCfpRepo,
                eventRepo: OrgaEventRepo,
                partnerRepo: OrgaPartnerRepo,
                venueRepo: OrgaVenueRepo,
                proposalRepo: OrgaProposalRepo,
                groupSettingsRepo: GroupSettingsRepo,
                meetupSrvEth: Either[String, MeetupSrv],
                emailSrv: EmailSrv,
                ms: MessageSrv,
                bus: MessageBus[Message]) extends UICtrl(cc, silhouette, conf) with UICtrl.OrgaAction {
  def list(group: Group.Slug, params: Page.Params): Action[AnyContent] = OrgaAction(group) { implicit req =>
    for {
      events <- eventRepo.listFull(params)
      proposals <- proposalRepo.list(events.items.flatMap(_.talks))
      speakers <- userRepo.list(proposals.flatMap(_.users))
    } yield Ok(html.list(events, proposals, speakers)(listBreadcrumb))
  }

  def create(group: Group.Slug): Action[AnyContent] = OrgaAction(group) { implicit req =>
    createView(group, GsForms.event)
  }

  def doCreate(group: Group.Slug): Action[AnyContent] = OrgaAction(group) { implicit req =>
    GsForms.event.bindFromRequest.fold(
      formWithErrors => createView(group, formWithErrors),
      data => (for {
        // TODO check if slug not already exist
        eventElt <- OptionT.liftF(eventRepo.create(data))
        _ <- OptionT.liftF(ms.eventCreated(eventElt).map(bus.publish))
      } yield Redirect(routes.EventCtrl.detail(group, data.slug))).value.map(_.getOrElse(groupNotFound(group)))
    )
  }

  private def createView(group: Group.Slug, form: Form[Event.Data])(implicit req: OrgaReq[AnyContent], ctx: OrgaCtx): IO[Result] = {
    for {
      meetupAccount <- groupSettingsRepo.findMeetup
      eventDescription <- groupSettingsRepo.findEventDescription
      filledForm = if (form.hasErrors) form else form.bind(Map("description.value" -> eventDescription.value)).discardingErrors
      b = listBreadcrumb.add("New" -> routes.EventCtrl.create(group))
    } yield Ok(html.create(meetupAccount.isDefined, filledForm)(b))
  }

  def detail(group: Group.Slug, event: Event.Slug, params: Page.Params): Action[AnyContent] = OrgaAction(group) { implicit req =>
    val customParams = params.defaultSize(40).defaultOrderBy(proposalRepo.fields.created)
    (for {
      e <- OptionT(eventRepo.findFull(event))
      eventProposals <- OptionT.liftF(proposalRepo.list(e.talks))
      eventTemplates <- OptionT.liftF(groupSettingsRepo.findEventTemplates)
      cfpProposals <- OptionT.liftF(e.cfp.map(cfp => proposalRepo.listFull(cfp.slug, Proposal.Status.Pending, customParams)).getOrElse(IO.pure(Page.empty[Proposal.Full])))
      speakers <- OptionT.liftF(userRepo.list((eventProposals.flatMap(_.users) ++ cfpProposals.items.flatMap(_.users)).distinct))
      userRatings <- OptionT.liftF(proposalRepo.listRatings(cfpProposals.items.map(_.id)))
      info <- OptionT.liftF(ms.eventInfo(e.event))
      desc = e.description.render(info).getOrElse(Markdown("")) // FIXME
      b = breadcrumb(e.event)
      res = Ok(html.detail(e.event, e.venue, eventProposals, desc, e.cfp, cfpProposals, speakers, userRatings, eventTemplates, GsForms.eventNotes.fill(e.orgaNotes.text))(b))
    } yield res).value.map(_.getOrElse(eventNotFound(group, event)))
  }

  def edit(group: Group.Slug, event: Event.Slug, redirect: Option[String]): Action[AnyContent] = OrgaAction(group) { implicit req =>
    editView(group, event, GsForms.event, redirect)
  }

  def doEdit(group: Group.Slug, event: Event.Slug, redirect: Option[String]): Action[AnyContent] = OrgaAction(group) { implicit req =>
    GsForms.event.bindFromRequest.fold(
      formWithErrors => editView(group, event, formWithErrors, redirect),
      data => for {
        eventOpt <- eventRepo.find(data.slug)
        res <- eventOpt match {
          case Some(duplicate) if data.slug != event =>
            val form = GsForms.event.fillAndValidate(data).withError("slug", s"Slug already taken by event: ${duplicate.name.value}")
            editView(group, event, form, redirect)
          case _ => eventRepo.edit(event, data).map { _ => redirectOr(redirect, routes.EventCtrl.detail(group, data.slug)) }
        }
      } yield res
    )
  }

  private def editView(group: Group.Slug, event: Event.Slug, form: Form[Event.Data], redirect: Option[String])(implicit req: OrgaReq[AnyContent], ctx: OrgaCtx): IO[Result] = {
    (for {
      eventElt <- OptionT(eventRepo.find(event))
      meetupAccount <- OptionT.liftF(groupSettingsRepo.findMeetup)
      b = breadcrumb(eventElt).add("Edit" -> routes.EventCtrl.edit(group, event))
      filledForm = if (form.hasErrors) form else form.fill(eventElt.data)
    } yield Ok(html.edit(meetupAccount.isDefined, eventElt, filledForm, redirect)(b))).value.map(_.getOrElse(eventNotFound(group, event)))
  }

  def doEditNotes(group: Group.Slug, event: Event.Slug): Action[AnyContent] = OrgaAction(group) { implicit req =>
    GsForms.eventNotes.bindFromRequest.fold(
      formWithErrors => IO.pure(Redirect(routes.EventCtrl.detail(group, event)).flashing("error" -> s"Unable to edit notes: ${req.formatErrors(formWithErrors)}")),
      notes => eventRepo.editNotes(event, notes).map(_ => Redirect(routes.EventCtrl.detail(group, event)))
    )
  }

  def setVenue(group: Group.Slug, event: Event.Slug, params: Page.Params): Action[AnyContent] = OrgaAction(group) { implicit req =>
    (for {
      e <- OptionT(eventRepo.findFull(event))
      venues <- OptionT.liftF(venueRepo.listCommon(params))
      groupVenues <- OptionT.liftF(venueRepo.listAllFull())
      res = Ok(html.setVenue(e.event, e.venue, venues, groupVenues)(setVenueBreadcrumb(e.event)))
    } yield res).value.map(_.getOrElse(eventNotFound(group, event)))
  }

  def doSetVenue(group: Group.Slug, event: Event.Slug, venue: Venue.Id, public: Boolean): Action[AnyContent] = OrgaAction(group) { implicit req =>
    (for {
      eventElt <- OptionT(eventRepo.find(event))
      venueToSet <- OptionT.liftF(if (public) {
        venueRepo.duplicate(venue).map(_._2.id)
      } else {
        IO.pure(venue)
      })
      _ <- OptionT.liftF(eventRepo.edit(event, eventElt.data.copy(venue = Some(venueToSet))))
      res = Redirect(routes.EventCtrl.detail(group, event)).flashing("success" -> "Venue updated")
    } yield res).value.map(_.getOrElse(eventNotFound(group, event)))
  }

  def createVenue(group: Group.Slug, event: Event.Slug): Action[AnyContent] = OrgaAction(group) { implicit req =>
    (for {
      eventElt <- OptionT(eventRepo.find(event))
      res = Ok(html.createVenue(eventElt, GsForms.venueWithPartner)(createVenueBreadcrumb(eventElt)))
    } yield res).value.map(_.getOrElse(eventNotFound(group, event)))
  }

  def doCreateVenue(group: Group.Slug, event: Event.Slug): Action[AnyContent] = OrgaAction(group) { implicit req =>
    GsForms.venueWithPartner.bindFromRequest().fold(
      formWithErrors => (for {
        eventElt <- OptionT(eventRepo.find(event))
        res = Ok(html.createVenue(eventElt, formWithErrors)(createVenueBreadcrumb(eventElt)))
      } yield res).value.map(_.getOrElse(eventNotFound(group, event))),
      data => (for {
        eventElt <- OptionT(eventRepo.find(event))
        partner <- OptionT.liftF(partnerRepo.create(data.toPartner))
        venue <- OptionT.liftF(venueRepo.create(partner.id, data.toVenue))
        _ <- OptionT.liftF(eventRepo.edit(event, eventElt.data.copy(venue = Some(venue.id))))
        res = Redirect(routes.EventCtrl.detail(group, event)).flashing("success" -> "Venue updated")
      } yield res).value.map(_.getOrElse(eventNotFound(group, event)))
    )
  }

  def attachCfp(group: Group.Slug, event: Event.Slug): Action[AnyContent] = OrgaAction(group) { implicit req =>
    GsForms.eventCfp.bindFromRequest.fold(
      formWithErrors => IO.pure(Redirect(routes.EventCtrl.detail(group, event)).flashing("error" -> s"Unable to attach CFP: ${req.formatErrors(formWithErrors)}")),
      cfp => eventRepo.attachCfp(event, cfp).map(_ => Redirect(routes.EventCtrl.detail(group, event)))
    )
  }

  def addToTalks(group: Group.Slug, event: Event.Slug, talk: Proposal.Id, params: Page.Params): Action[AnyContent] = OrgaAction(group) { implicit req =>
    (for {
      eventElt <- OptionT(eventRepo.find(event))
      cfpElt <- OptionT(cfpRepo.find(eventElt.id))
      proposalElt <- OptionT(proposalRepo.find(cfpElt.slug, talk))
      _ <- OptionT.liftF(eventRepo.editTalks(event, eventElt.add(talk).talks))
      _ <- OptionT.liftF(proposalRepo.accept(cfpElt.slug, talk, eventElt.id))
      _ <- OptionT(ms.proposalAddedToEvent(cfpElt, proposalElt, eventElt)).map(bus.publish)
    } yield Redirect(routes.EventCtrl.detail(group, event, params))).value.map(_.getOrElse(eventNotFound(group, event)))
  }

  def cancelTalk(group: Group.Slug, event: Event.Slug, talk: Proposal.Id, params: Page.Params): Action[AnyContent] = OrgaAction(group) { implicit req =>
    (for {
      eventElt <- OptionT(eventRepo.find(event))
      cfpElt <- OptionT(cfpRepo.find(eventElt.id))
      proposalElt <- OptionT(proposalRepo.find(cfpElt.slug, talk))
      _ <- OptionT.liftF(eventRepo.editTalks(event, eventElt.remove(talk).talks))
      _ <- OptionT.liftF(proposalRepo.cancel(cfpElt.slug, talk, eventElt.id))
      _ <- OptionT(ms.proposalRemovedFromEvent(cfpElt, proposalElt, eventElt)).map(bus.publish)
    } yield Redirect(routes.EventCtrl.detail(group, event, params))).value.map(_.getOrElse(eventNotFound(group, event)))
  }

  def moveTalk(group: Group.Slug, event: Event.Slug, talk: Proposal.Id, up: Boolean, params: Page.Params): Action[AnyContent] = OrgaAction(group) { implicit req =>
    (for {
      eventElt <- OptionT(eventRepo.find(event))
      _ <- OptionT.liftF(eventRepo.editTalks(event, eventElt.move(talk, up).talks))
    } yield Redirect(routes.EventCtrl.detail(group, event, params))).value.map(_.getOrElse(eventNotFound(group, event)))
  }

  def rejectProposal(group: Group.Slug, event: Event.Slug, talk: Proposal.Id, params: Page.Params): Action[AnyContent] = OrgaAction(group) { implicit req =>
    (for {
      eventElt <- OptionT(eventRepo.find(event))
      cfpElt <- OptionT(cfpRepo.find(eventElt.id))
      _ <- OptionT.liftF(proposalRepo.reject(cfpElt.slug, talk))
    } yield Redirect(routes.EventCtrl.detail(group, event, params))).value.map(_.getOrElse(eventNotFound(group, event)))
  }

  def publish(group: Group.Slug, event: Event.Slug): Action[AnyContent] = OrgaAction(group) { implicit req =>
    publishView(group, event, GsForms.eventPublish.fill(PublishOptions.default))
  }

  def doPublish(group: Group.Slug, event: Event.Slug): Action[AnyContent] = OrgaAction(group) { implicit req =>
    GsForms.eventPublish.bindFromRequest.fold(
      formWithErrors => publishView(group, event, formWithErrors),
      data => (for {
        e <- OptionT(eventRepo.findFull(event))
        info <- OptionT.liftF(ms.eventInfo(e.event))
        description = e.description.render(info).getOrElse(Markdown("")) // FIXME
        meetupAccount <- OptionT.liftF(groupSettingsRepo.findMeetup)
        meetup <- OptionT.liftF((for {
          meetupSrv <- meetupSrvEth.toOption
          creds <- meetupAccount
          info <- data.meetup if info.publish
        } yield meetupSrv.publish(e.event, e.venue, description, info.draft, conf.app.aesKey, creds)).sequence)
        _ <- OptionT.liftF(meetup.map(_._1).filter(_ => e.refs.meetup.isEmpty)
          .map(ref => e.event.copy(refs = e.refs.copy(meetup = Some(ref))))
          .map(eventElt => eventRepo.edit(event, eventElt.data)).sequence)
        _ <- OptionT.liftF(meetup.flatMap(m => m._2.flatMap(r => e.venue.map(v => (r, v)))).filter { case (_, v) => v.refs.meetup.isEmpty }
          .map { case (ref, venue) => venueRepo.edit(venue.id, venue.data.copy(refs = venue.refs.copy(meetup = Some(ref)))) }.sequence)
        _ <- OptionT.liftF(eventRepo.publish(event))
        _ <- if (data.notifyMembers) {
          OptionT.liftF(groupRepo.listMembers
            .flatMap(members => members.map(m => emailSrv.send(Emails.eventPublished(e.event, e.venue, m))).sequence))
        } else {
          OptionT.liftF(IO.pure(List.empty[Done]))
        }
        _ <- OptionT.liftF(ms.eventPublished(e.event).map(bus.publish))
      } yield Redirect(routes.EventCtrl.detail(group, event))).value.map(_.getOrElse(eventNotFound(group, event))))
      .recover {
        case NonFatal(e) => Redirect(routes.EventCtrl.detail(group, event)).flashing("error" -> s"An error happened: ${e.getMessage}")
      }
  }

  private def publishView(group: Group.Slug, event: Event.Slug, form: Form[PublishOptions])(implicit req: OrgaReq[AnyContent], ctx: OrgaCtx): IO[Result] = {
    (for {
      e <- OptionT(eventRepo.find(event))
      info <- OptionT.liftF(ms.eventInfo(e))
      description = e.description.render(info).getOrElse(Markdown("")) // FIXME
      meetupAccount <- OptionT.liftF(groupSettingsRepo.findMeetup)
      b = breadcrumb(e).add("Publish" -> routes.EventCtrl.publish(group, event))
      res = Ok(html.publish(e, description, form, meetupAccount.isDefined)(b))
    } yield res).value.map(_.getOrElse(groupNotFound(group)))
  }


  def contactRsvps(group: Group.Slug, event: Event.Slug): Action[AnyContent] = OrgaAction(group) { implicit req =>
    contactRsvpsView(group, event, GsForms.eventContact)
  }

  def doContactRsvps(group: Group.Slug, event: Event.Slug): Action[AnyContent] = OrgaAction(group) { implicit req =>
    GsForms.eventContact.bindFromRequest.fold(
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
  }

  private def contactRsvpsView(group: Group.Slug, event: Event.Slug, form: Form[GsForms.EventContact])(implicit req: OrgaReq[AnyContent], ctx: OrgaCtx): IO[Result] = {
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

  def setVenueBreadcrumb(event: Event)(implicit req: OrgaReq[AnyContent]): Breadcrumb =
    breadcrumb(event).add("Set venue" -> routes.EventCtrl.setVenue(req.group.slug, event.slug))

  def createVenueBreadcrumb(event: Event)(implicit req: OrgaReq[AnyContent]): Breadcrumb =
    setVenueBreadcrumb(event).add("New" -> routes.EventCtrl.createVenue(req.group.slug, event.slug))
}
