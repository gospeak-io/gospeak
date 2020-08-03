package gospeak.web.pages.orga

import cats.data.OptionT
import cats.effect.IO
import com.mohiva.play.silhouette.api.Silhouette
import gospeak.core.domain._
import gospeak.core.domain.messages.Message
import gospeak.core.domain.utils.{Constants, SocialAccounts}
import gospeak.core.services.email.EmailSrv
import gospeak.core.services.meetup.MeetupSrv
import gospeak.core.services.meetup.domain._
import gospeak.core.services.places.PlacesSrv
import gospeak.core.services.storage._
import gospeak.libs.scala.Extensions._
import gospeak.libs.scala.StringUtils
import gospeak.libs.scala.domain._
import gospeak.web.AppConf
import gospeak.web.auth.domain.CookieEnv
import gospeak.web.domain._
import gospeak.web.emails.Emails
import gospeak.web.pages.orga.GroupCtrl._
import gospeak.web.pages.orga.settings.SettingsCtrl
import gospeak.web.pages.orga.settings.routes.{SettingsCtrl => SettingsRoutes}
import gospeak.web.pages.user.UserCtrl
import gospeak.web.pages.user.routes.{UserCtrl => UserRoutes}
import gospeak.web.utils.{GsForms, OrgaReq, UICtrl, UserReq, _}
import play.api.data.Form
import play.api.mvc.{Action, AnyContent, ControllerComponents, Result}

import scala.util.control.NonFatal

class GroupCtrl(cc: ControllerComponents,
                silhouette: Silhouette[CookieEnv],
                conf: AppConf,
                userRepo: OrgaUserRepo,
                val groupRepo: OrgaGroupRepo,
                venueRepo: OrgaVenueRepo,
                eventRepo: OrgaEventRepo,
                proposalRepo: OrgaProposalRepo,
                sponsorRepo: OrgaSponsorRepo,
                sponsorPackRepo: OrgaSponsorPackRepo,
                partnerRepo: OrgaPartnerRepo,
                userRequestRepo: OrgaUserRequestRepo,
                groupSettingsRepo: OrgaGroupSettingsRepo,
                emailSrv: EmailSrv,
                meetupSrv: MeetupSrv,
                placesSrv: PlacesSrv) extends UICtrl(cc, silhouette, conf) with UICtrl.OrgaAction {
  def create(): Action[AnyContent] = UserAction { implicit req =>
    createForm(GsForms.group)
  }

  def doCreate(): Action[AnyContent] = UserAction { implicit req =>
    GsForms.group.bindFromRequest.fold(
      formWithErrors => createForm(formWithErrors),
      data => groupRepo.create(data).map(_ => Redirect(routes.GroupCtrl.detail(data.slug)))
    )
  }

  private def createForm(form: Form[Group.Data])(implicit req: UserReq[AnyContent]): IO[Result] = {
    val b = groupBreadcrumb.add("New" -> routes.GroupCtrl.create())
    IO.pure(Ok(html.create(form)(b)))
  }

  def meetupConnect(): Action[AnyContent] = UserAction { implicit req =>
    val redirectUri = routes.GroupCtrl.meetupCallback().absoluteURL(meetupSrv.hasSecureCallback)
    meetupSrv.buildAuthorizationUrl(redirectUri).map(url => Redirect(url.value)).toIO
  }

  def meetupCallback(): Action[AnyContent] = UserAction { implicit req =>
    val redirectUri = routes.GroupCtrl.meetupCallback().absoluteURL(meetupSrv.hasSecureCallback)
    req.getQueryString("code").map { code =>
      (for {
        token <- meetupSrv.requestAccessToken(redirectUri, code, conf.app.aesKey)
        meetupGroups <- meetupSrv.getOwnedGroups(conf.app.aesKey)(token)
        b = groupBreadcrumb.add("New" -> routes.GroupCtrl.create())
        next = Ok(html.importMeetup(GsForms.meetupImport, meetupGroups, MeetupToken.toText(token))(b))
      } yield next).recover { case NonFatal(e) => Redirect(routes.GroupCtrl.create()).flashing("error" -> e.getMessage) }
    }.getOrElse {
      val state = req.getQueryString("state")
      val error = req.getQueryString("error")
      val msg = s"Failed to authenticate with meetup${error.map(e => s", reason: $e").getOrElse("")}${state.map(s => s" (state: $s)").getOrElse("")}"
      IO.pure(Redirect(routes.GroupCtrl.create()).flashing("error" -> msg))
    }
  }

  def meetupImport(): Action[AnyContent] = UserAction { implicit req =>
    GsForms.meetupImport.bindFromRequest.fold(
      formWithErrors => IO.pure(Redirect(routes.GroupCtrl.create()).flashing(formWithErrors.flash)),
      { case (token, meetupSlug) => (for {
        loggedUser <- meetupSrv.getLoggedUser(conf.app.aesKey)(token)
        meetupGroup <- meetupSrv.getGroup(meetupSlug, conf.app.aesKey)(token)
        creds = MeetupCredentials(token, loggedUser, meetupGroup)
        meetupEvents <- meetupSrv.getEvents(meetupSlug, conf.app.aesKey, creds)
        meetupVenues = meetupEvents.flatMap(_.venue).groupBy(_.id).values.toList.map(_.head)
        groupData <- toDomain(meetupGroup)
        group <- groupRepo.create(groupData)
        ctx = req.orga(group)
        _ = (for { // launched async to not wait too long before redirecting to the group dashboard
          _ <- groupSettingsRepo.set(conf.gospeak.defaultGroupSettings.set(creds))(ctx)
          venuesData <- meetupVenues.map(toDomain(meetupSlug, _)).sequence
          venueIds <- venuesData.map { case (i, p, v) => partnerRepo.create(p)(ctx).flatMap(pa => venueRepo.create(pa.id, v)(ctx).map(ve => (i, ve.id))) }.sequence.map(_.toMap)
          eventsData <- meetupEvents.map(toDomain(meetupSlug, _, venueIds)).sequence.toIO
          _ <- eventsData.map(eventRepo.create(_)(ctx)).sequence
        } yield ()).unsafeRunAsync(_ => ())
      } yield Redirect(routes.GroupCtrl.detail(group.slug)).flashing("success" -> s"You created the <b>${group.name.value}</b> group on Gospeak!"))
        .recover { case NonFatal(e) => Redirect(routes.GroupCtrl.create()).flashing("error" -> e.getMessage) }
      })
  }

  private def toDomain(g: MeetupGroup): IO[Group.Data] = for {
    slug <- Group.Slug.from(g.slug.value.toLowerCase).toIO
    location <- placesSrv.find(g.address, g.location)
  } yield Group.Data(
    slug = slug,
    name = Group.Name(g.name),
    logo = g.logo.map(Logo),
    banner = None,
    contact = None,
    website = None,
    description = g.description,
    location = location,
    social = SocialAccounts.fromUrls(meetup = Some(g.link)),
    tags = (List(g.category) ++ g.topics.take(4)).map(Tag(_)))

  private def toDomain(group: MeetupGroup.Slug, v: MeetupVenue): IO[(MeetupVenue.Id, Partner.Data, Venue.Data)] = for {
    slug <- Partner.Slug.from(StringUtils.slugify(v.name)).toIO
    logo <- Url.from(Constants.Placeholders.partnerLogo).map(Logo).toIO
    place = s"${v.address}, ${v.city}, ${v.country}"
    location <- placesSrv.find(place, v.geo).flatMap(_.toIO(CustomException(s"Place not found ($place)")))
  } yield (v.id, Partner.Data(
    slug = slug,
    name = Partner.Name(v.name),
    notes = Markdown(""),
    description = None,
    logo = logo,
    social = SocialAccounts.fromUrls()
  ), Venue.Data(
    contact = None,
    address = location,
    notes = Markdown(""),
    roomSize = None,
    refs = Venue.ExtRefs(meetup = Some(MeetupVenue.Ref(group, v.id)))))

  private def toDomain(group: MeetupGroup.Slug, e: MeetupEvent, venues: Map[MeetupVenue.Id, Venue.Id]): Either[CustomException, Event.Data] = for {
    slug <- Event.Slug.from(StringUtils.slugify(e.name))
  } yield Event.Data(
    cfp = None,
    slug = slug,
    name = Event.Name(e.name),
    kind = Event.Kind.Meetup,
    start = e.start,
    maxAttendee = e.rsvp_limit,
    allowRsvp = false,
    venue = e.venue.flatMap(v => venues.get(v.id)),
    description = LiquidMarkdown[Message.EventInfo](e.description.map(_.value).getOrElse("")),
    tags = Seq(),
    refs = Event.ExtRefs(meetup = Some(MeetupEvent.Ref(group, e.id))))

  def join(params: Page.Params): Action[AnyContent] = UserAction { implicit req =>
    for {
      groups <- groupRepo.listJoinable(params)
      pendingRequests <- userRequestRepo.listPendingUserToJoinAGroupRequests
      owners <- userRepo.list(groups.items.flatMap(_.owners.toList).distinct)
      b = groupBreadcrumb.add("Join" -> routes.GroupCtrl.join())
    } yield Ok(html.join(groups, owners, pendingRequests)(b))
  }

  def doJoin(group: Group.Slug, params: Page.Params): Action[AnyContent] = UserAction { implicit req =>
    (for {
      groupElt <- OptionT(groupRepo.find(group))
      _ <- OptionT.liftF(userRequestRepo.createUserAskToJoinAGroup(groupElt.id))
    } yield Redirect(UserRoutes.index()).flashing("success" -> s"Join request sent to <b>${groupElt.name.value}</b> group"))
      .value.map(_.getOrElse(Redirect(routes.GroupCtrl.join(params)).flashing("error" -> s"Unable to send join request to <b>$group</b>")))
  }

  def detail(group: Group.Slug): Action[AnyContent] = OrgaAction(group) { implicit req =>
    for {
      stats <- groupRepo.getStats
      events <- eventRepo.listAfter(Page.Params.defaults.withOrderBy("start"))
      proposals <- proposalRepo.list(events.items.flatMap(_.talks))
      speakers <- userRepo.list(proposals.flatMap(_.users).distinct)
      sponsors <- sponsorRepo.listAll.map(_.groupBy(_.partner))
      partners <- partnerRepo.list(sponsors.keys.toSeq)
      currentSponsors = sponsors.flatMap { case (id, ss) => partners.find(_.id == id).flatMap(p => ss.find(_.isCurrent(req.now)).map(s => (p, (s, ss.length)))) }.toSeq.sortBy(_._2._1.finish.toEpochDay)
      pastSponsors = sponsors.filter(_._2.forall(!_.isCurrent(req.now))).flatMap { case (id, s) => partners.find(_.id == id).map(p => (p, s)) }.toSeq.sortBy(s => -s._2.map(_.finish.toEpochDay).max)
      packs <- sponsorPackRepo.listAll
      requests <- userRequestRepo.listPendingGroupRequests
      requestUsers <- userRepo.list(requests.flatMap(_.users).distinct)
    } yield Ok(html.detail(stats, events, proposals, speakers, currentSponsors, pastSponsors, packs, requests, requestUsers)(breadcrumb))
  }

  def edit(group: Group.Slug, redirect: Option[String]): Action[AnyContent] = OrgaAction(group) { implicit req =>
    editForm(GsForms.group, redirect)
  }

  def doEdit(group: Group.Slug, redirect: Option[String]): Action[AnyContent] = OrgaAction(group) { implicit req =>
    GsForms.group.bindFromRequest.fold(
      formWithErrors => editForm(formWithErrors, redirect),
      data => for {
        newSlugExits <- groupRepo.exists(data.slug)
        res <- if (newSlugExits && data.slug != group) {
          editForm(GsForms.group.fillAndValidate(data).withError("slug", s"Slug ${data.slug.value} already taken by an other group"), redirect)
        } else {
          groupRepo.edit(data).map(_ => redirectOr(redirect, SettingsRoutes.settings(data.slug)))
        }
      } yield res
    )
  }

  private def editForm(form: Form[Group.Data], redirect: Option[String])(implicit req: OrgaReq[AnyContent]): IO[Result] = {
    val filledForm = if (form.hasErrors) form else form.fill(req.group.data)
    val b = SettingsCtrl.listBreadcrumb.add("Edit group" -> routes.GroupCtrl.edit(req.group.slug))
    IO.pure(Ok(html.edit(filledForm, redirect)(b)))
  }

  def acceptJoin(group: Group.Slug, userRequest: UserRequest.Id): Action[AnyContent] = OrgaAction(group) { implicit req =>
    (for {
      requestElt <- OptionT(userRequestRepo.findPendingUserToJoinAGroup(userRequest))
      userElt <- OptionT(userRepo.find(requestElt.createdBy))
      _ <- OptionT.liftF(userRequestRepo.acceptUserToJoinAGroup(requestElt))
      _ <- OptionT.liftF(emailSrv.send(Emails.joinGroupAccepted(userElt)))
      next = redirectToPreviousPageOr(routes.GroupCtrl.detail(group))
      msg = s"You accepted <b>${userElt.name.value}</b> as organizer of ${req.group.name.value}"
    } yield next.flashing("success" -> msg)).value.map(_.getOrElse(groupNotFound(group)))
  }

  def rejectJoin(group: Group.Slug, userRequest: UserRequest.Id): Action[AnyContent] = OrgaAction(group) { implicit req =>
    (for {
      requestElt <- OptionT(userRequestRepo.findPendingUserToJoinAGroup(userRequest))
      userElt <- OptionT(userRepo.find(requestElt.createdBy))
      _ <- OptionT.liftF(userRequestRepo.rejectUserToJoinAGroup(requestElt))
      _ <- OptionT.liftF(emailSrv.send(Emails.joinGroupRejected(userElt)))
      next = redirectToPreviousPageOr(routes.GroupCtrl.detail(group))
      msg = s"You refused to <b>${userElt.name.value}</b> to join ${req.group.name.value} as an organizer"
    } yield next.flashing("error" -> msg)).value.map(_.getOrElse(groupNotFound(group)))
  }

  def contactMembers(group: Group.Slug): Action[AnyContent] = OrgaAction(group) { implicit req =>
    contactMembersView(GsForms.groupContact)
  }

  def doContactMembers(group: Group.Slug): Action[AnyContent] = OrgaAction(group) { implicit req =>
    GsForms.groupContact.bindFromRequest.fold(
      formWithErrors => contactMembersView(formWithErrors),
      data => (for {
        sender <- OptionT(IO.pure(req.group.senders(req.user).find(_.address == data.from)))
        members <- OptionT.liftF(groupRepo.listMembers)
        _ <- OptionT.liftF(members.map(m => emailSrv.send(Emails.groupMessage(req.group, sender, data.subject, data.content, m))).sequence)
        next = Redirect(routes.GroupCtrl.detail(group)).flashing("success" -> "Message sent to group members")
      } yield next).value.map(_.getOrElse(groupNotFound(group)))
    )
  }

  private def contactMembersView(form: Form[GsForms.GroupContact])(implicit req: OrgaReq[AnyContent]): IO[Result] = {
    val b = breadcrumb.add("Contact members" -> routes.GroupCtrl.contactMembers(req.group.slug))
    IO.pure(Ok(html.contactMembers(form)(b)))
  }
}

object GroupCtrl {
  def groupBreadcrumb(implicit req: UserReq[AnyContent]): Breadcrumb =
    UserCtrl.breadcrumb.add("Groups" -> UserRoutes.index())

  def breadcrumb(implicit req: OrgaReq[AnyContent]): Breadcrumb =
    Breadcrumb(req.group.name.value, routes.GroupCtrl.detail(req.group.slug))
}
