package gospeak.web.pages.published.groups

import cats.data.OptionT
import cats.effect.IO
import com.mohiva.play.silhouette.api.Silhouette
import gospeak.core.domain.{Event, Group, Proposal}
import gospeak.core.services.email.EmailSrv
import gospeak.core.services.meetup.MeetupSrv
import gospeak.core.services.storage._
import gospeak.libs.scala.Extensions._
import gospeak.libs.scala.domain._
import gospeak.web.AppConf
import gospeak.web.auth.domain.CookieEnv
import gospeak.web.domain.Breadcrumb
import gospeak.web.emails.Emails
import gospeak.web.pages.published.HomeCtrl
import gospeak.web.pages.published.groups.GroupCtrl._
import gospeak.web.services.MessageSrv
import gospeak.web.services.MessageSrv._
import gospeak.web.utils._
import play.api.mvc._
import play.filters.headers.SecurityHeadersFilter

import scala.util.control.NonFatal

class GroupCtrl(cc: ControllerComponents,
                silhouette: Silhouette[CookieEnv],
                conf: AppConf,
                userRepo: PublicUserRepo,
                groupRepo: PublicGroupRepo,
                cfpRepo: PublicCfpRepo,
                eventRepo: PublicEventRepo,
                proposalRepo: PublicProposalRepo,
                sponsorRepo: PublicSponsorRepo,
                sponsorPackRepo: PublicSponsorPackRepo,
                commentRepo: PublicCommentRepo,
                groupSettingsRepo: PublicGroupSettingsRepo,
                meetupSrv: MeetupSrv,
                emailSrv: EmailSrv,
                ms: MessageSrv) extends UICtrl(cc, silhouette, conf) {
  def list(params: Page.Params): Action[AnyContent] = UserAwareAction { implicit req =>
    for {
      groups <- groupRepo.listFull(params)
      orgas <- userRepo.list(groups.items.flatMap(_.owners.toList))
    } yield Ok(html.list(groups, orgas)(listBreadcrumb()))
  }

  def detail(group: Group.Slug): Action[AnyContent] = UserAwareAction { implicit req =>
    (for {
      groupElt <- OptionT(groupRepo.findFull(group))
      speakerCount <- OptionT.liftF(userRepo.speakerCountPublic(groupElt.id))
      cfps <- OptionT.liftF(cfpRepo.listAllIncoming(groupElt.id))
      events <- OptionT.liftF(eventRepo.listPublished(groupElt.id, Page.Params.defaults))
      sponsors <- OptionT.liftF(sponsorRepo.listCurrentFull(groupElt.id, req.now))
      packs <- OptionT.liftF(sponsorPackRepo.listActives(groupElt.id))
      orgas <- OptionT.liftF(userRepo.list(groupElt.owners.toList))
      userMembership <- OptionT.liftF(req.user.map(_.id).map(groupRepo.findActiveMember(groupElt.id, _)).sequence.map(_.flatten))
      res = Ok(html.detail(groupElt, speakerCount, cfps, events, sponsors, packs, orgas, userMembership)(breadcrumb(groupElt.group)))
    } yield res).value.map(_.getOrElse(publicGroupNotFound(group)))
  }

  def doJoin(group: Group.Slug): Action[AnyContent] = UserAction { implicit req =>
    (for {
      groupElt <- OptionT(groupRepo.find(group))
      userMembership <- OptionT.liftF(groupRepo.findActiveMember(groupElt.id, req.user.id))
      msg <- OptionT.liftF(userMembership.swap.map(_ => groupRepo.join(groupElt.id)(req.user, req.now)
        .map(_ => "success" -> s"You are now a member of <b>${groupElt.name.value}</b>")
        .recover { case NonFatal(e) => "error" -> s"Can't join <b>${groupElt.name.value}</b>: ${e.getMessage}" })
        .getOrElse(IO.pure("success" -> s"You are already a member of <b>${groupElt.name.value}</b>")))
      next = redirectToPreviousPageOr(routes.GroupCtrl.detail(group)).flashing(msg)
    } yield next).value.map(_.getOrElse(publicGroupNotFound(group)))
  }

  def doLeave(group: Group.Slug): Action[AnyContent] = UserAction { implicit req =>
    (for {
      groupElt <- OptionT(groupRepo.find(group))
      userMembership <- OptionT.liftF(groupRepo.findActiveMember(groupElt.id, req.user.id))
      msg <- OptionT.liftF(userMembership.map(groupRepo.leave(_)(req.user.id, req.now)
        .map(_ => "success" -> s"You have leaved <b>${groupElt.name.value}</b>")
        .recover { case NonFatal(e) => "error" -> s"Can't leave <b>${groupElt.name.value}</b>, error: ${e.getMessage}" })
        .getOrElse(IO.pure("error" -> s"You are not a member of <b>${groupElt.name.value}</b>")))
      next = redirectToPreviousPageOr(routes.GroupCtrl.detail(group)).flashing(msg)
    } yield next).value.map(_.getOrElse(publicGroupNotFound(group)))
  }

  def events(group: Group.Slug, params: Page.Params): Action[AnyContent] = UserAwareAction { implicit req =>
    (for {
      groupElt <- OptionT(groupRepo.findFull(group))
      speakerCount <- OptionT.liftF(userRepo.speakerCountPublic(groupElt.id))
      cfps <- OptionT.liftF(cfpRepo.listAllIncoming(groupElt.id))
      events <- OptionT.liftF(eventRepo.listPublished(groupElt.id, params))
      sponsors <- OptionT.liftF(sponsorRepo.listCurrentFull(groupElt.id, req.now))
      packs <- OptionT.liftF(sponsorPackRepo.listActives(groupElt.id))
      orgas <- OptionT.liftF(userRepo.list(groupElt.owners.toList))
      userMembership <- OptionT.liftF(req.user.map(_.id).map(groupRepo.findActiveMember(groupElt.id, _)).sequence.map(_.flatten))
      res = Ok(html.events(groupElt, speakerCount, cfps, events, sponsors, packs, orgas, userMembership)(breadcrumbEvents(groupElt.group)))
    } yield res).value.map(_.getOrElse(publicGroupNotFound(group)))
  }

  def event(group: Group.Slug, event: Event.Slug): Action[AnyContent] = UserAwareAction { implicit req =>
    eventView(group, event)
  }

  def eventDrawMeetupAttendee(group: Group.Slug, event: Event.Slug): Action[AnyContent] = UserAwareAction { implicit req =>
    (for {
      groupElt <- OptionT(groupRepo.find(group))
      eventElt <- OptionT(eventRepo.findPublished(groupElt.id, event))
      creds <- OptionT.liftF(groupSettingsRepo.findMeetup(groupElt.id))
      attendees <- OptionT.liftF(eventElt.event.refs.meetup.flatMap { r =>
        creds.map(c => meetupSrv.getAttendees(r.group, r.event, conf.app.aesKey, c).map(_.right[String]).recover { case NonFatal(e) => Left(e.getMessage) })
      }.getOrElse(IO.pure(Left("No meetup reference for this event or configured credentials"))))
      cleanAttendees = attendees.map(_.filter(a => a.id.value != 0L && a.response == "yes" && !a.host))
      res = Ok(html.attendeeDraw(eventElt, cleanAttendees)).withHeaders(SecurityHeadersFilter.X_FRAME_OPTIONS_HEADER -> "SAMEORIGIN")
    } yield res).value.map(_.getOrElse(publicEventNotFound(group, event)))
  }

  def showTemplate(group: Group.Slug, event: Event.Slug, templateName: String): Action[AnyContent] = UserAwareAction { implicit req =>
    (for {
      groupElt <- OptionT(groupRepo.find(group))
      eventElt <- OptionT(eventRepo.findPublished(groupElt.id, event))
      info <- OptionT.liftF(ms.eventInfo(eventElt))
      eventTemplates <- OptionT.liftF(groupSettingsRepo.findEventTemplates(groupElt.id))
      res = eventTemplates.get(templateName).toEither(s"Template '$templateName' not found")
        .flatMap(_.render(info).left.map(_.message))
        .fold(
          err => Redirect(routes.GroupCtrl.event(group, event)).flashing("error" -> err),
          text => Ok(html.showTemplate(Html(text))).withHeaders(SecurityHeadersFilter.X_FRAME_OPTIONS_HEADER -> "SAMEORIGIN"))
    } yield res).value.map(_.getOrElse(publicEventNotFound(group, event)))
  }

  def doSendComment(group: Group.Slug, event: Event.Slug): Action[AnyContent] = UserAction { implicit req =>
    val next = redirectToPreviousPageOr(routes.GroupCtrl.event(group, event))
    GsForms.comment.bindFromRequest.fold(
      formWithErrors => IO.pure(next.flashing(formWithErrors.flash)),
      data => (for {
        groupElt <- OptionT(groupRepo.find(group))
        eventElt <- OptionT(eventRepo.findPublished(groupElt.id, event))
        orgas <- OptionT.liftF(userRepo.list(groupElt.owners.toList))
        comment <- OptionT.liftF(commentRepo.addComment(eventElt.id, data))
        _ <- OptionT.liftF(orgas.map(o => emailSrv.send(Emails.eventCommentAdded(groupElt, eventElt.event, o, comment))).sequence)
      } yield next).value.map(_.getOrElse(publicEventNotFound(group, event)))
    )
  }

  private def eventView(group: Group.Slug, event: Event.Slug)(implicit req: UserAwareReq[AnyContent]): IO[Result] = {
    (for {
      groupElt <- OptionT(groupRepo.find(group))
      proposalTweetTmpl <- OptionT.liftF(groupSettingsRepo.findProposalTweet(groupElt.id))
      eventElt <- OptionT(eventRepo.findPublished(groupElt.id, event))
      proposals <- OptionT.liftF(proposalRepo.listPublicFull(eventElt.talks))
      proposalsWithTweet <- OptionT.liftF(proposals.map(p => ms.proposalInfo(p).map { i =>
        p -> Tweet.from(proposalTweetTmpl, i, req.toAbsolute(routes.GroupCtrl.talk(group, p.id))).toOption
      }).sequence)
      speakers <- OptionT.liftF(userRepo.list(proposals.flatMap(_.speakers.toList).distinct))
      comments <- OptionT.liftF(commentRepo.getComments(eventElt.id))
      yesRsvp <- OptionT.liftF(eventRepo.countYesRsvp(eventElt.id))
      userMembership <- OptionT.liftF(req.user.map(_.id).map(groupRepo.findActiveMember(groupElt.id, _)).sequence.map(_.flatten))
      userRsvp <- OptionT.liftF(req.user.map(_.id).map(eventRepo.findRsvp(eventElt.id, _)).sequence.map(_.flatten))
      rsvps <- OptionT.liftF(eventRepo.listRsvps(eventElt.id))
      info <- OptionT.liftF(ms.eventInfo(eventElt))
      description = eventElt.description.render(info).getOrElse(Markdown("")) // FIXME
      b = breadcrumbEvent(groupElt, eventElt)
      res = Ok(html.event(groupElt, eventElt, description, proposalsWithTweet, speakers, comments, yesRsvp, userMembership, userRsvp, rsvps)(b))
    } yield res).value.map(_.getOrElse(publicEventNotFound(group, event)))
  }

  def doRsvp(group: Group.Slug, event: Event.Slug, answer: Event.Rsvp.Answer): Action[AnyContent] = UserAction { implicit req =>
    import Event.Rsvp.Answer.{No, Wait, Yes}
    val waitMsg = "warning" -> "Thanks but this event is already full. You are on waiting list, your place will be reserved as soon as there is more places"
    val yesMsg = "success" -> "You seat is booked!"
    val noMsg = "success" -> "Thanks for answering, see you an other time"
    (for {
      groupElt <- OptionT(groupRepo.find(group))
      eventElt <- OptionT(eventRepo.findPublished(groupElt.id, event))
      _ <- OptionT.liftF(if (eventElt.canRsvp(req.now)) IO.pure(Done) else IO.raiseError(CustomException("Can't RSVP for now")))
      yesRsvp <- OptionT.liftF(eventRepo.countYesRsvp(eventElt.id))
      userMembership <- OptionT.liftF(groupRepo.findActiveMember(groupElt.id, req.user.id))
      _ <- OptionT.liftF(userMembership.map(_ => IO.pure(Done)).getOrElse(groupRepo.join(groupElt.id)(req.user, req.now)))
      userRsvp <- OptionT.liftF(eventRepo.findRsvp(eventElt.id, req.user.id))
      msg <- OptionT.liftF((userRsvp.map(_.answer), answer) match {
        case (_, Wait) => IO.pure("error" -> "Can't answer Wait on Rsvp")
        case (None, No) => eventRepo.createRsvp(eventElt.id, No)(req.user, req.now).map(_ => noMsg)
        case (None, Yes) =>
          if (eventElt.isFull(yesRsvp)) eventRepo.createRsvp(eventElt.id, Wait)(req.user, req.now).map(_ => waitMsg)
          else eventRepo.createRsvp(eventElt.id, Yes)(req.user, req.now).map(_ => yesMsg)
        case (Some(Yes), Yes) | (Some(Wait), Yes) | (Some(No), No) => IO.pure("success" -> "Thanks")
        case (Some(Yes), No) => for {
          _ <- eventRepo.editRsvp(eventElt.id, No)(req.user, req.now)
          firstWait <- eventRepo.findFirstWait(eventElt.id)
          _ <- firstWait.map { r =>
            eventRepo.editRsvp(eventElt.id, Yes)(r.user, req.now)
              .flatMap(_ => emailSrv.send(Emails.movedFromWaitingListToAttendees(groupElt, eventElt.event, r.user)))
          }.getOrElse(IO.pure(Done))
        } yield noMsg
        case (Some(Wait), No) => eventRepo.editRsvp(eventElt.id, No)(req.user, req.now).map(_ => noMsg)
        case (Some(No), Yes) =>
          if (eventElt.isFull(yesRsvp)) eventRepo.editRsvp(eventElt.id, Wait)(req.user, req.now).map(_ => waitMsg)
          else eventRepo.editRsvp(eventElt.id, Yes)(req.user, req.now).map(_ => yesMsg)
      })
      next = Redirect(routes.GroupCtrl.event(group, event)).flashing(msg)
    } yield next).value.map(_.getOrElse(publicGroupNotFound(group))).recover {
      case e: CustomException => Redirect(routes.GroupCtrl.event(group, event)).flashing("error" -> e.message)
      case NonFatal(e) => Redirect(routes.GroupCtrl.event(group, event)).flashing("error" -> s"Unexpected error: ${e.getMessage}")
    }
  }

  def talks(group: Group.Slug, params: Page.Params): Action[AnyContent] = UserAwareAction { implicit req =>
    (for {
      groupElt <- OptionT(groupRepo.findFull(group))
      proposalTweetTmpl <- OptionT.liftF(groupSettingsRepo.findProposalTweet(groupElt.id))
      speakerCount <- OptionT.liftF(userRepo.speakerCountPublic(groupElt.id))
      proposals <- OptionT.liftF(proposalRepo.listPublicFull(groupElt.id, params.defaultOrderBy("title")))
      proposalsWithTweet <- OptionT.liftF(proposals.items.map(p => ms.proposalInfo(p).map { i =>
        p -> Tweet.from(proposalTweetTmpl, i, req.toAbsolute(routes.GroupCtrl.talk(group, p.id))).toOption
      }).sequence.map(p => proposals.copy(items = p)))
      speakers <- OptionT.liftF(userRepo.list(proposals.items.flatMap(_.speakers.toList).distinct))
      cfps <- OptionT.liftF(cfpRepo.listAllIncoming(groupElt.id))
      sponsors <- OptionT.liftF(sponsorRepo.listCurrentFull(groupElt.id, req.now))
      packs <- OptionT.liftF(sponsorPackRepo.listActives(groupElt.id))
      orgas <- OptionT.liftF(userRepo.list(groupElt.owners.toList))
      userMembership <- OptionT.liftF(req.user.map(_.id).map(groupRepo.findActiveMember(groupElt.id, _)).sequence.map(_.flatten))
      res = Ok(html.proposals(groupElt, speakerCount, proposalsWithTweet, cfps, speakers, sponsors, packs, orgas, userMembership)(breadcrumbEvents(groupElt.group)))
    } yield res).value.map(_.getOrElse(publicGroupNotFound(group)))
  }

  def talk(group: Group.Slug, proposal: Proposal.Id): Action[AnyContent] = UserAwareAction { implicit req =>
    (for {
      groupElt <- OptionT(groupRepo.find(group))
      proposalTweetTmpl <- OptionT.liftF(groupSettingsRepo.findProposalTweet(groupElt.id))
      proposalElt <- OptionT(proposalRepo.findPublicFull(groupElt.id, proposal))
      tweet <- OptionT.liftF(ms.proposalInfo(proposalElt).map(i => Tweet.from(proposalTweetTmpl, i, req.toAbsolute(routes.GroupCtrl.talk(group, proposalElt.id))).toOption))
      speakers <- OptionT.liftF(userRepo.list(proposalElt.speakers.toList))
      res = Ok(html.proposal(groupElt, proposalElt, tweet, speakers)(breadcrumbTalk(groupElt, proposalElt)))
    } yield res).value.map(_.getOrElse(publicProposalNotFound(group, proposal)))
  }

  def speakers(group: Group.Slug, params: Page.Params): Action[AnyContent] = UserAwareAction { implicit req =>
    (for {
      groupElt <- OptionT(groupRepo.findFull(group))
      speakerCount <- OptionT.liftF(userRepo.speakerCountPublic(groupElt.id))
      speakers <- OptionT.liftF(userRepo.speakersPublic(groupElt.id, params))
      cfps <- OptionT.liftF(cfpRepo.listAllIncoming(groupElt.id))
      sponsors <- OptionT.liftF(sponsorRepo.listCurrentFull(groupElt.id, req.now))
      packs <- OptionT.liftF(sponsorPackRepo.listActives(groupElt.id))
      orgas <- OptionT.liftF(userRepo.list(groupElt.owners.toList))
      userMembership <- OptionT.liftF(req.user.map(_.id).map(groupRepo.findActiveMember(groupElt.id, _)).sequence.map(_.flatten))
      res = Ok(html.speakers(groupElt, speakerCount, cfps, speakers, sponsors, packs, orgas, userMembership)(breadcrumbEvents(groupElt.group)))
    } yield res).value.map(_.getOrElse(publicGroupNotFound(group)))
  }

  def members(group: Group.Slug, params: Page.Params): Action[AnyContent] = UserAwareAction { implicit req =>
    (for {
      groupElt <- OptionT(groupRepo.findFull(group))
      speakerCount <- OptionT.liftF(userRepo.speakerCountPublic(groupElt.id))
      members <- OptionT.liftF(groupRepo.listMembers(groupElt.id, params))
      cfps <- OptionT.liftF(cfpRepo.listAllIncoming(groupElt.id))
      sponsors <- OptionT.liftF(sponsorRepo.listCurrentFull(groupElt.id, req.now))
      packs <- OptionT.liftF(sponsorPackRepo.listActives(groupElt.id))
      orgas <- OptionT.liftF(userRepo.list(groupElt.owners.toList))
      userMembership <- OptionT.liftF(req.user.map(_.id).map(groupRepo.findActiveMember(groupElt.id, _)).sequence.map(_.flatten))
      res = Ok(html.members(groupElt, speakerCount, cfps, members, sponsors, packs, orgas, userMembership)(breadcrumbEvents(groupElt.group)))
    } yield res).value.map(_.getOrElse(publicGroupNotFound(group)))
  }

  def contactOrga(group: Group.Slug): Action[AnyContent] = UserAction { implicit req =>
    val next = Redirect(routes.GroupCtrl.detail(group))
    GsForms.orgaContact.bindFromRequest.fold(
      formWithErrors => IO.pure(next.flashing(formWithErrors.flash)),
      data => (for {
        groupElt <- OptionT(groupRepo.find(group))
        orgas <- OptionT.liftF(userRepo.list(groupElt.owners.toList))
        _ <- OptionT.liftF(orgas.toNel.map(o => emailSrv.send(Emails.contactOrga(data.subject, data.content, o))).sequence)
        res = next.flashing("success" -> "The message has been sent!")
      } yield res).value.map(_.getOrElse(groupNotFound(group))))
  }
}

object GroupCtrl {
  def listBreadcrumb(): Breadcrumb =
    HomeCtrl.breadcrumb().add("Groups" -> routes.GroupCtrl.list())

  def breadcrumb(group: Group): Breadcrumb =
    listBreadcrumb().add(group.name.value -> routes.GroupCtrl.detail(group.slug))

  def breadcrumbEvents(group: Group): Breadcrumb =
    breadcrumb(group).add("Events" -> routes.GroupCtrl.events(group.slug))

  def breadcrumbEvent(group: Group, event: Event.Full): Breadcrumb =
    breadcrumbEvents(group).add(event.name.value -> routes.GroupCtrl.event(group.slug, event.slug))

  def breadcrumbTalks(group: Group): Breadcrumb =
    breadcrumb(group).add("Talks" -> routes.GroupCtrl.talks(group.slug))

  def breadcrumbTalk(group: Group, proposal: Proposal.Full): Breadcrumb =
    breadcrumbTalks(group).add(proposal.title.value -> routes.GroupCtrl.talk(group.slug, proposal.id))

  def breadcrumbSpeakers(group: Group): Breadcrumb =
    breadcrumb(group).add("Speakers" -> routes.GroupCtrl.speakers(group.slug))

  def breadcrumbMembers(group: Group): Breadcrumb =
    breadcrumb(group).add("Members" -> routes.GroupCtrl.members(group.slug))
}
