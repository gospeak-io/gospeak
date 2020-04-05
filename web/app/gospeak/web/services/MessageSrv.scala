package gospeak.web.services

import java.time.{Instant, LocalDate, LocalDateTime}

import cats.data.{NonEmptyList, OptionT}
import cats.effect.IO
import gospeak.core.domain._
import gospeak.core.domain.messages.Message._
import gospeak.core.domain.messages._
import gospeak.core.domain.utils.SocialAccounts.SocialAccount.TwitterAccount
import gospeak.core.domain.utils.{Constants, Info, SocialAccounts}
import gospeak.core.services.meetup.domain.{MeetupEvent, MeetupGroup, MeetupVenue}
import gospeak.core.services.storage._
import gospeak.libs.scala.Extensions._
import gospeak.libs.scala.StringUtils.leftPad
import gospeak.libs.scala.domain._
import gospeak.web.services.MessageSrv._
import gospeak.web.utils.{BasicReq, OrgaReq, UserAwareReq, UserReq}
import io.circe.generic.extras.Configuration
import io.circe.generic.extras.semiauto.deriveConfiguredEncoder
import io.circe.{Encoder, Json}
import play.api.mvc.AnyContent

import scala.concurrent.duration._

class MessageSrv(groupRepo: OrgaGroupRepo,
                 cfpRepo: OrgaCfpRepo,
                 venueRepo: OrgaVenueRepo,
                 proposalRepo: OrgaProposalRepo,
                 sponsorRepo: OrgaSponsorRepo,
                 userRepo: OrgaUserRepo) {
  def eventCreated(event: Event)(implicit req: OrgaReq[AnyContent]): IO[EventCreated] =
    groupAndEventData(req.group, event, req.now)(req.userAware).map { case (g, e) => EventCreated(g, e, embed(req.user), req.now) }

  def eventPublished(event: Event)(implicit req: OrgaReq[AnyContent]): IO[EventPublished] =
    groupAndEventData(req.group, event, req.now)(req.userAware).map { case (g, e) => EventPublished(g, e, embed(req.user), req.now) }

  def eventInfo(event: Event)(implicit req: OrgaReq[AnyContent]): IO[EventInfo] =
    groupAndEventData(req.group, event, req.now)(req.userAware).map { case (g, e) => EventInfo(g, e) }

  def eventInfo(group: Group, event: Event)(implicit req: UserAwareReq[AnyContent]): IO[EventInfo] =
    groupAndEventData(group, event, req.now).map { case (g, e) => EventInfo(g, e) }

  def proposalCreated(cfp: Cfp, proposal: Proposal)(implicit req: UserReq[AnyContent]): IO[Option[ProposalCreated]] = (for {
    group <- OptionT(groupRepo.find(cfp.group))
    sponsors <- OptionT.liftF(sponsorRepo.listCurrentFull(group.id, req.now))
    users <- OptionT.liftF(userRepo.list(group.users ++ proposal.users))
  } yield ProposalCreated(msg(group, sponsors, users), msg(group, cfp), msg(group, cfp, proposal, users), embed(req.user), req.now)).value

  def proposalAddedToEvent(cfp: Cfp, proposal: Proposal, event: Event)(implicit req: OrgaReq[AnyContent]): IO[Option[ProposalAddedToEvent]] = (for {
    group <- OptionT(groupRepo.find(cfp.group))
    sponsors <- OptionT.liftF(sponsorRepo.listCurrentFull(group.id, req.now))
    (cfps, venues, proposals) <- OptionT.liftF(eventData(event)(req.userAware))
    users <- OptionT.liftF(userRepo.list(group.users ++ proposals.flatMap(_.users) ++ proposal.users))
  } yield ProposalAddedToEvent(msg(group, sponsors, users), msg(group, cfp), msg(group, cfp, proposal, users), msg(group, event, cfps, venues, proposals, users), embed(req.user), req.now)).value

  def proposalRemovedFromEvent(cfp: Cfp, proposal: Proposal, event: Event)(implicit req: OrgaReq[AnyContent]): IO[Option[ProposalRemovedFromEvent]] = (for {
    group <- OptionT(groupRepo.find(cfp.group))
    sponsors <- OptionT.liftF(sponsorRepo.listCurrentFull(group.id, req.now))
    (cfps, venues, proposals) <- OptionT.liftF(eventData(event)(req.userAware))
    users <- OptionT.liftF(userRepo.list(group.users ++ proposals.flatMap(_.users) ++ proposal.users))
  } yield ProposalRemovedFromEvent(msg(group, sponsors, users), msg(group, cfp), msg(group, cfp, proposal, users), msg(group, event, cfps, venues, proposals, users), embed(req.user), req.now)).value

  def proposalInfo(proposal: Proposal.Full)(implicit req: UserAwareReq[AnyContent]): IO[ProposalInfo] = for {
    sponsors <- sponsorRepo.listCurrentFull(proposal.group.id, req.now)
    venues <- venueRepo.listAllFull(proposal.group.id, proposal.event.flatMap(_.venue).toList)
    users <- userRepo.list((proposal.users ++ venues.flatMap(_.users)).distinct)
    msgGroup = msg(proposal.group, sponsors, users)
    msgCfp = msg(proposal.group, proposal.cfp)
    msgProposal = msg(proposal.group, proposal.cfp, proposal.proposal, users)
    msgEvent = proposal.event.map(e => embed(proposal.group, e, venues))
  } yield ProposalInfo(msgGroup, msgCfp, msgProposal, msgEvent)

  def externalEventCreated(event: ExternalEvent)(implicit req: UserReq[AnyContent]): IO[ExternalEventCreated] =
    IO.pure(ExternalEventCreated(msg(event), embed(req.user), req.now))

  def externalEventUpdated(event: ExternalEvent)(implicit req: UserReq[AnyContent]): IO[ExternalEventUpdated] =
    IO.pure(ExternalEventUpdated(msg(event), embed(req.user), req.now))

  def externalCfpCreated(event: ExternalEvent, cfp: ExternalCfp)(implicit req: UserReq[AnyContent]): IO[ExternalCfpCreated] =
    IO.pure(ExternalCfpCreated(msg(event), msg(cfp), embed(req.user), req.now))

  def externalCfpUpdated(event: ExternalEvent, cfp: ExternalCfp)(implicit req: UserReq[AnyContent]): IO[ExternalCfpUpdated] =
    IO.pure(ExternalCfpUpdated(msg(event), msg(cfp), embed(req.user), req.now))

  private def groupAndEventData(group: Group, event: Event, now: Instant)(implicit req: UserAwareReq[AnyContent]): IO[(MsgGroup, MsgEvent)] = for {
    sponsors <- sponsorRepo.listCurrentFull(group.id, now)
    (cfps, venues, proposals) <- eventData(event)
    users <- userRepo.list(group.users ++ proposals.flatMap(_.users))
  } yield (msg(group, sponsors, users), msg(group, event, cfps, venues, proposals, users))

  private def eventData(event: Event)(implicit req: UserAwareReq[AnyContent]): IO[(Seq[Cfp], Seq[Venue.Full], Seq[Proposal.Full])] = for {
    cfps <- cfpRepo.list(event.cfp.toList)
    venues <- venueRepo.listAllFull(event.group, event.venue.toList)
    proposals <- proposalRepo.listFull(event.talks)
  } yield (cfps, venues, proposals)

  def sample(ref: Option[Message.Ref])(implicit req: UserReq[AnyContent]): Json = {
    import MessageSrv.Sample._
    import MessageSrv._

    ref match {
      case Some(Message.Ref.eventCreated) => eEventCreated(Message.EventCreated(msgGroup, msgEvent, msgUser, now))
      case Some(Message.Ref.eventPublished) => eEventPublished(Message.EventPublished(msgGroup, msgEvent, msgUser, now))
      case Some(Message.Ref.eventInfo) => eEventInfo(Message.EventInfo(msgGroup, msgEvent))
      case Some(Message.Ref.proposalCreated) => eProposalCreated(Message.ProposalCreated(msgGroup, msgCfp, msgProposal, msgUser, now))
      case Some(Message.Ref.proposalAddedToEvent) => eProposalAddedToEvent(Message.ProposalAddedToEvent(msgGroup, msgCfp, msgProposal, msgEvent, msgUser, now))
      case Some(Message.Ref.proposalRemovedFromEvent) => eProposalRemovedFromEvent(Message.ProposalRemovedFromEvent(msgGroup, msgCfp, msgProposal, msgEvent, msgUser, now))
      case Some(Message.Ref.proposalInfo) => eProposalInfo(Message.ProposalInfo(msgGroup, msgCfp, msgProposal, Some(msgEvent.embed)))
      case Some(Message.Ref.externalEventCreated) => eExternalEventCreated(Message.ExternalEventCreated(msgExternalEvent, msgUser, now))
      case Some(Message.Ref.externalEventUpdated) => eExternalEventUpdated(Message.ExternalEventUpdated(msgExternalEvent, msgUser, now))
      case Some(Message.Ref.externalCfpCreated) => eExternalCfpCreated(Message.ExternalCfpCreated(msgExternalEvent, msgExternalCfp, msgUser, now))
      case Some(Message.Ref.externalCfpUpdated) => eExternalCfpUpdated(Message.ExternalCfpUpdated(msgExternalEvent, msgExternalCfp, msgUser, now))
      case Some(_) => Json.obj()
      case None => Json.obj()
    }
  }
}

object MessageSrv {
  private implicit val configuration: Configuration = Configuration.default.withDiscriminator("type")

  private implicit val eLogo: Encoder[Logo] = (v: Logo) => Json.fromString(v.value)
  private implicit val eBanner: Encoder[Banner] = (v: Banner) => Json.fromString(v.value)
  private implicit val eAvatar: Encoder[Avatar] = (v: Avatar) => Json.fromString(v.value)
  private implicit val eEmailAddress: Encoder[EmailAddress] = (v: EmailAddress) => Json.fromString(v.value)
  private implicit val eFiniteDuration: Encoder[FiniteDuration] = (v: FiniteDuration) => Json.fromString(v.toString)
  private implicit val eLocalDateTime: Encoder[LocalDateTime] = (v: LocalDateTime) => Json.obj(
    "year" -> Json.fromString(leftPad(v.getYear.toString, 4, '0')),
    "month" -> Json.fromString(leftPad(v.getMonthValue.toString, 2, '0')),
    "monthStr" -> Json.fromString(v.getMonth.name().toLowerCase.capitalize),
    "day" -> Json.fromString(leftPad(v.getDayOfMonth.toString, 2, '0')),
    "dayStr" -> Json.fromString(v.getDayOfWeek.name().toLowerCase.capitalize),
    "hour" -> Json.fromString(leftPad(v.getHour.toString, 2, '0')),
    "minute" -> Json.fromString(leftPad(v.getMinute.toString, 2, '0')))
  private implicit val eInstant: Encoder[Instant] = (v: Instant) => eLocalDateTime.apply(v.atZone(Constants.defaultZoneId).toLocalDateTime)
  private implicit val eUrl: Encoder[Url] = (v: Url) => Json.fromString(v.value)
  private implicit val eSlides: Encoder[Slides] = (v: Slides) => Json.fromString(v.value)
  private implicit val eVideo: Encoder[Video] = (v: Video) => Json.fromString(v.value)
  private implicit val eMarkdown: Encoder[Markdown] = (v: Markdown) => Json.obj(
    "full" -> Json.fromString(v.value),
    "short1" -> Json.fromString(v.value.split("\n").head.take(140)),
    "short2" -> Json.fromString(v.value.split("\n").head.take(280)),
    "short3" -> Json.fromString(v.value.take(280)))
  private implicit val eMustacheMarkdown: Encoder[Mustache.Markdown[Message.EventInfo]] = (v: Mustache.Markdown[Message.EventInfo]) => Json.obj(
    "full" -> Json.fromString(v.value),
    "short1" -> Json.fromString(v.value.split("\n").head.take(140)),
    "short2" -> Json.fromString(v.value.split("\n").head.take(280)),
    "short3" -> Json.fromString(v.value.take(280)))
  private implicit val eGeo: Encoder[Geo] = deriveConfiguredEncoder[Geo]
  private implicit val eGMapPlace: Encoder[GMapPlace] = (v: GMapPlace) => Json.obj(
    "full" -> Json.fromString(v.value),
    "city" -> Json.fromString(v.valueShort),
    "link" -> Json.fromString(v.url))
  private implicit val eSocialAccounts: Encoder[SocialAccounts] = (v: SocialAccounts) => Json.obj(v.all.map(a => (a.name, Json.obj(
    "link" -> Json.fromString(a.link),
    "handle" -> Json.fromString(a.handle)))): _*)
  private implicit val eTwitterAccount: Encoder[TwitterAccount] = (v: TwitterAccount) => Json.fromString(v.link)
  private implicit val eTwitterHashtag: Encoder[TwitterHashtag] = (v: TwitterHashtag) => Json.fromString(v.value)
  private implicit val eTag: Encoder[Tag] = (v: Tag) => Json.fromString(v.value)

  private implicit val eMeetupGroupSlug: Encoder[MeetupGroup.Slug] = (v: MeetupGroup.Slug) => Json.fromString(v.value)
  private implicit val eMeetupEventId: Encoder[MeetupEvent.Id] = (v: MeetupEvent.Id) => Json.fromLong(v.value)
  private implicit val eMeetupEventRef: Encoder[MeetupEvent.Ref] = deriveConfiguredEncoder[MeetupEvent.Ref]

  private implicit val eUserSlug: Encoder[User.Slug] = (v: User.Slug) => Json.fromString(v.value)
  private implicit val eUserName: Encoder[User.Name] = (v: User.Name) => Json.fromString(v.value)
  private implicit val eGroupSlug: Encoder[Group.Slug] = (v: Group.Slug) => Json.fromString(v.value)
  private implicit val eGroupName: Encoder[Group.Name] = (v: Group.Name) => Json.fromString(v.value)
  private implicit val eGroupStatus: Encoder[Group.Status] = (v: Group.Status) => Json.fromString(v.value)
  private implicit val eCfpSlug: Encoder[Cfp.Slug] = (v: Cfp.Slug) => Json.fromString(v.value)
  private implicit val eCfpName: Encoder[Cfp.Name] = (v: Cfp.Name) => Json.fromString(v.value)
  private implicit val ePartnerSlug: Encoder[Partner.Slug] = (v: Partner.Slug) => Json.fromString(v.value)
  private implicit val ePartnerName: Encoder[Partner.Name] = (v: Partner.Name) => Json.fromString(v.value)
  private implicit val eVenueId: Encoder[Venue.Id] = (v: Venue.Id) => Json.fromString(v.value)
  private implicit val eTalkTitle: Encoder[Talk.Title] = (v: Talk.Title) => Json.fromString(v.value)
  private implicit val eProposalId: Encoder[Proposal.Id] = (v: Proposal.Id) => Json.fromString(v.value)
  private implicit val eEventSlug: Encoder[Event.Slug] = (v: Event.Slug) => Json.fromString(v.value)
  private implicit val eEventName: Encoder[Event.Name] = (v: Event.Name) => Json.fromString(v.value)
  private implicit val eEventKind: Encoder[Event.Kind] = (v: Event.Kind) => Json.fromString(v.value)
  private implicit val eSponsorPackName: Encoder[SponsorPack.Name] = (v: SponsorPack.Name) => Json.fromString(v.value)

  private implicit val eMsgUserEmbed: Encoder[MsgUser.Embed] = deriveConfiguredEncoder[MsgUser.Embed]
  private implicit val eMsgCfpEmbed: Encoder[MsgCfp.Embed] = deriveConfiguredEncoder[MsgCfp.Embed]
  private implicit val eMsgPartnerEmbed: Encoder[MsgPartner.Embed] = deriveConfiguredEncoder[MsgPartner.Embed]
  private implicit val eMsgVenueEmbed: Encoder[MsgVenue.Embed] = deriveConfiguredEncoder[MsgVenue.Embed]
  private implicit val eMsgProposalEmbed: Encoder[MsgProposal.Embed] = deriveConfiguredEncoder[MsgProposal.Embed]
  private implicit val eMsgEventEmbed: Encoder[MsgEvent.Embed] = deriveConfiguredEncoder[MsgEvent.Embed]
  private implicit val eMsgSponsorEmbed: Encoder[MsgSponsor.Embed] = deriveConfiguredEncoder[MsgSponsor.Embed]
  private implicit val eMsgUser: Encoder[MsgUser] = deriveConfiguredEncoder[MsgUser]
  private implicit val eMsgGroup: Encoder[MsgGroup] = deriveConfiguredEncoder[MsgGroup]
  private implicit val eMsgCfp: Encoder[MsgCfp] = deriveConfiguredEncoder[MsgCfp]
  private implicit val eMsgVenue: Encoder[MsgVenue] = deriveConfiguredEncoder[MsgVenue]
  private implicit val eMsgProposal: Encoder[MsgProposal] = deriveConfiguredEncoder[MsgProposal]
  private implicit val eMsgEvent: Encoder[MsgEvent] = deriveConfiguredEncoder[MsgEvent]
  private implicit val eMsgSponsor: Encoder[MsgSponsor] = deriveConfiguredEncoder[MsgSponsor]
  private implicit val eMsgExternalEvent: Encoder[MsgExternalEvent] = deriveConfiguredEncoder[MsgExternalEvent]
  private implicit val eMsgExternalCfp: Encoder[MsgExternalCfp] = deriveConfiguredEncoder[MsgExternalCfp]

  implicit val eEventCreated: Encoder[EventCreated] = deriveConfiguredEncoder[EventCreated]
  implicit val eEventPublished: Encoder[EventPublished] = deriveConfiguredEncoder[EventPublished]
  implicit val eEventInfo: Encoder[EventInfo] = deriveConfiguredEncoder[EventInfo]
  implicit val eProposalCreated: Encoder[ProposalCreated] = deriveConfiguredEncoder[ProposalCreated]
  implicit val eProposalAddedToEvent: Encoder[ProposalAddedToEvent] = deriveConfiguredEncoder[ProposalAddedToEvent]
  implicit val eProposalRemovedFromEvent: Encoder[ProposalRemovedFromEvent] = deriveConfiguredEncoder[ProposalRemovedFromEvent]
  implicit val eProposalInfo: Encoder[ProposalInfo] = deriveConfiguredEncoder[ProposalInfo]
  implicit val eExternalEventCreated: Encoder[ExternalEventCreated] = deriveConfiguredEncoder[ExternalEventCreated]
  implicit val eExternalEventUpdated: Encoder[ExternalEventUpdated] = deriveConfiguredEncoder[ExternalEventUpdated]
  implicit val eExternalCfpCreated: Encoder[ExternalCfpCreated] = deriveConfiguredEncoder[ExternalCfpCreated]
  implicit val eExternalCfpUpdated: Encoder[ExternalCfpUpdated] = deriveConfiguredEncoder[ExternalCfpUpdated]

  implicit val eMessage: Encoder[Message] = deriveConfiguredEncoder[Message]

  object Sample {
    val now: Instant = Instant.now()
    private val nowLD: LocalDate = LocalDate.now()
    private val nowLDT: LocalDateTime = LocalDateTime.now()
    private val paris: GMapPlace = GMapPlace(
      id = "ChIJD7fiBh9u5kcRYJSMaMOCCwQ",
      name = "Paris",
      streetNo = None,
      street = None,
      postalCode = None,
      locality = Some("Paris"),
      country = "France",
      formatted = "Paris, France",
      input = "Paris, France",
      geo = Geo(48.85661400000001, 2.3522219000000177),
      url = "https://maps.google.com/?q=Paris,+France&ftid=0x47e66e1f06e2b70f:0x40b82c3688c9460",
      website = Some("http://www.paris.fr/"),
      phone = None,
      utcOffset = 60)
    private val user: User = User(
      id = User.Id.generate(),
      slug = User.Slug.from("loicknuchel").get,
      status = User.Status.Public,
      firstName = "Loïc",
      lastName = "Knuchel",
      email = EmailAddress.from("loicknuchel@gmail.com").get,
      emailValidated = Some(now),
      emailValidationBeforeLogin = true,
      avatar = Avatar(Url.from("https://avatars0.githubusercontent.com/u/653009").get),
      title = Some("Scala developer"),
      bio = Some(Markdown("I have worked at many companies and created a bunch of projects such as **Gospeak**")),
      mentoring = Some(Markdown("I can help you reviewing your CFP abstract or slides")),
      company = Some("Gospeak"),
      location = Some("Paris"),
      phone = Some("06 66 66 66 66"),
      website = Some(Url.from("http://loic.knuchel.org/").get),
      social = SocialAccounts.fromUrls(
        twitter = Some(Url.Twitter.from("https://twitter.com/loicknuchel").get)),
      createdAt = now,
      updatedAt = now)
    private val group: Group = Group(
      id = Group.Id.generate(),
      slug = Group.Slug.from("humantalks-paris").get,
      name = Group.Name("HumanTalks Paris"),
      logo = Some(Logo(Url.from("https://avatars2.githubusercontent.com/u/16031887").get)),
      banner = Some(Banner(Url.from("https://pbs.twimg.com/profile_banners/1568284584/1564061329/1500x500").get)),
      contact = Some(EmailAddress.from("paris@humantalks.com").get),
      website = Some(Url.from("https://www.humantalks.paris").get),
      description = Markdown("Discover new tech things **every month**"),
      location = Some(paris),
      owners = NonEmptyList.of(user.id),
      social = SocialAccounts.fromUrls(
        twitter = Some(Url.Twitter.from("https://twitter.com/HumanTalksParis").get)),
      tags = Seq(Tag("tech"), Tag("meetup")),
      status = Group.Status.Active,
      info = Info(user.id, now))
    private val cfp: Cfp = Cfp(
      id = Cfp.Id.generate(),
      group = group.id,
      slug = Cfp.Slug.from("ht-paris").get,
      name = Cfp.Name("HumanTalks Paris"),
      begin = Some(nowLDT),
      close = Some(nowLDT),
      description = Markdown("Submit a talk you are **passionated** about!"),
      tags = Seq(Tag("tech")),
      info = Info(user.id, now))
    private val partner: Partner = Partner(
      id = Partner.Id.generate(),
      group = group.id,
      slug = Partner.Slug.from("gospeak").get,
      name = Partner.Name("Gospeak"),
      notes = Markdown("A *very good* partner (private note)"),
      description = Some(Markdown("A platform to **help** everyone to become a speaker")),
      logo = Constants.Gospeak.logo,
      social = SocialAccounts.fromUrls(
        twitter = Some(Constants.Gospeak.twitter)),
      info = Info(user.id, now))
    private val contact: Contact = Contact(
      id = Contact.Id.generate(),
      partner = partner.id,
      firstName = Contact.FirstName("Loïc"),
      lastName = Contact.LastName("Knuchel"),
      email = EmailAddress.from("loicknuchel@gmail.com").get,
      notes = Markdown("**Private** notes about this contact"),
      info = Info(user.id, now))
    private val venue: Venue = Venue(
      id = Venue.Id.generate(),
      partner = partner.id,
      contact = Some(contact.id),
      address = paris,
      notes = Markdown("A **private** note about the venue"),
      roomSize = Some(100),
      refs = Venue.ExtRefs(meetup = Some(MeetupVenue.Ref(group = MeetupGroup.Slug.from("HumanTalks-Paris").get, venue = MeetupVenue.Id(123)))),
      info = Info(user.id, now))
    private val talk: Talk = Talk(
      id = Talk.Id.generate(),
      slug = Talk.Slug.from("why-fp").get,
      status = Talk.Status.Public,
      title = Talk.Title("Why FP?"),
      duration = 10.minutes,
      description = Markdown("The *best* talk about FP"),
      message = Markdown("Hi CFP committee, **take your chance** to have this talk!"),
      speakers = NonEmptyList.of(user.id),
      slides = Some(Slides.from("https://www.slideshare.net/loicknuchel/comprendre-la-programmation-fonctionnelle-blend-web-mix-le-02112016").get),
      video = Some(Video.from("https://www.youtube.com/watch?v=PH93yIhsG7k").get),
      tags = Seq(Tag("FP")),
      info = Info(user.id, now))
    private val eventId: Event.Id = Event.Id.generate()
    private val proposal: Proposal = Proposal(
      id = Proposal.Id.generate(),
      talk = talk.id,
      cfp = cfp.id,
      event = Some(eventId),
      status = Proposal.Status.Accepted,
      title = Talk.Title("Why FP?"),
      duration = 10.minutes,
      description = Markdown("The *best* talk about FP"),
      message = Markdown("Hi CFP committee, **take your chance** to have this talk!"),
      speakers = NonEmptyList.of(user.id),
      slides = Some(Slides.from("https://www.slideshare.net/loicknuchel/comprendre-la-programmation-fonctionnelle-blend-web-mix-le-02112016").get),
      video = Some(Video.from("https://www.youtube.com/watch?v=PH93yIhsG7k").get),
      tags = Seq(Tag("FP")),
      orgaTags = Seq(Tag("selected")),
      info = Info(user.id, now))
    private val event: Event = Event(
      id = eventId,
      group = group.id,
      cfp = Some(cfp.id),
      slug = Event.Slug.from("2020_04").get,
      name = Event.Name("HumanTalks Paris Avril 2020"),
      kind = Event.Kind.Meetup,
      start = nowLDT,
      maxAttendee = Some(100),
      allowRsvp = false,
      description = Mustache.Markdown[Message.EventInfo]("Thanks to **{{venue.name}}** for hosting"),
      orgaNotes = Event.Notes("We should add *more* talks", now, user.id),
      venue = Some(venue.id),
      talks = Seq(proposal.id),
      tags = Seq(Tag("culture")),
      published = Some(now),
      refs = Event.ExtRefs(meetup = Some(MeetupEvent.Ref(group = MeetupGroup.Slug.from("HumanTalks-Paris").get, event = MeetupEvent.Id(456)))),
      info = Info(user.id, now))
    private val sponsorPack: SponsorPack = SponsorPack(
      id = SponsorPack.Id.generate(),
      group = group.id,
      slug = SponsorPack.Slug.from("gold").get,
      name = SponsorPack.Name("Gold"),
      description = Markdown("**Best in class** sponsoring"),
      price = Price(1500, Price.Currency.EUR),
      duration = TimePeriod(1, TimePeriod.PeriodUnit.Year),
      active = true,
      info = Info(user.id, now))
    private val sponsor: Sponsor = Sponsor(
      id = Sponsor.Id.generate(),
      group = group.id,
      partner = partner.id,
      pack = sponsorPack.id,
      contact = Some(contact.id),
      start = nowLD,
      finish = nowLD,
      paid = Some(nowLD),
      price = Price(1500, Price.Currency.EUR),
      info = Info(user.id, now))
    private val externalEvent: ExternalEvent = ExternalEvent(
      id = ExternalEvent.Id.generate(),
      name = Event.Name("Sunny Tech 2019"),
      kind = Event.Kind.Conference,
      logo = Some(Logo(Url.from("https://res.cloudinary.com/gospeak/image/upload/ar_1,c_crop/v1576790534/ext-events/Sunny-Tech.svg").get)),
      description = Markdown("**Awesome** conference!!!"),
      start = Some(nowLDT),
      finish = Some(nowLDT),
      location = Some(paris),
      url = Some(Url.from("https://2019.sunny-tech.io").get),
      tickets = Some(Url.from("http://register.ncrafts.io").get),
      videos = Some(Url.from("https://www.youtube.com/playlist?list=PLuZ_sYdawLiXq_8YaaROhaUazHQVPiELa").get),
      twitterAccount = Some(TwitterAccount(Url.Twitter.from("https://twitter.com/sunnytech_mtp").get)),
      twitterHashtag = Some(TwitterHashtag.from("#SunnyTech2019").get),
      tags = Seq(Tag("tech")),
      info = Info(user.id, now))
    private val externalCfp = ExternalCfp(
      id = ExternalCfp.Id.generate(),
      event = externalEvent.id,
      description = Markdown("Submit your talks **now**"),
      begin = Some(nowLDT),
      close = Some(nowLDT),
      url = Url.from("https://sessionize.com/agile-france-2020").get,
      info = Info(user.id, now))

    private val venueFull: Venue.Full = Venue.Full(venue, partner, Some(contact))
    private val proposalFull: Proposal.Full = Proposal.Full(proposal, cfp, group, talk, Some(event), Some(venueFull), 0, None, 0, None, 0, 0, 0, None)
    private val sponsorFull: Sponsor.Full = Sponsor.Full(sponsor, sponsorPack, partner, Some(contact))

    private val users = Seq(user)
    private val cfps = Seq(cfp)
    private val venues = Seq(venueFull)
    private val proposals = Seq(proposalFull)
    private val sponsors = Seq(sponsorFull)

    val msgUser: MsgUser.Embed = embed(user)

    def msgGroup(implicit req: BasicReq[AnyContent]): MsgGroup = msg(group, sponsors, users)

    def msgCfp(implicit req: BasicReq[AnyContent]): MsgCfp = msg(group, cfp)

    def msgEvent(implicit req: BasicReq[AnyContent]): MsgEvent = msg(group, event, cfps, venues, proposals, users)

    def msgProposal(implicit req: BasicReq[AnyContent]): MsgProposal = msg(group, cfp, proposal, users)

    def msgExternalEvent(implicit req: BasicReq[AnyContent]): MsgExternalEvent = msg(externalEvent)

    def msgExternalCfp(implicit req: BasicReq[AnyContent]): MsgExternalCfp = msg(externalCfp)
  }

  private def msg(g: Group, sponsors: Seq[Sponsor.Full], users: Seq[User])(implicit req: BasicReq[AnyContent]): MsgGroup =
    MsgGroup(
      slug = g.slug,
      name = g.name,
      logo = g.logo,
      banner = g.banner,
      contact = g.contact,
      website = g.website,
      description = g.description,
      links = g.social,
      tags = g.tags,
      orgas = g.owners.map(id => users.find(_.id == id).map(embed).getOrElse(MsgUser.Embed.unknown(id))),
      sponsors = sponsors.map(embed),
      publicLink = req.format(gospeak.web.pages.published.groups.routes.GroupCtrl.detail(g.slug)),
      orgaLink = req.format(gospeak.web.pages.orga.routes.GroupCtrl.detail(g.slug)))

  private def msg(g: Group, c: Cfp)(implicit req: BasicReq[AnyContent]): MsgCfp =
    MsgCfp(
      slug = c.slug,
      name = c.name,
      active = c.isActive(req.nowLDT),
      publicLink = req.format(gospeak.web.pages.published.cfps.routes.CfpCtrl.detail(c.slug)),
      orgaLink = req.format(gospeak.web.pages.orga.cfps.routes.CfpCtrl.detail(g.slug, c.slug)))

  private def msg(g: Group, e: Event, cfps: Seq[Cfp], venues: Seq[Venue.Full], proposals: Seq[Proposal.Full], users: Seq[User])(implicit req: BasicReq[AnyContent]): MsgEvent =
    MsgEvent(
      slug = e.slug,
      name = e.name,
      kind = e.kind,
      start = e.start,
      description = e.description,
      cfp = e.cfp.map(id => cfps.find(_.id == id).map(embed(g, _)).getOrElse(MsgCfp.Embed.unknown(id))),
      venue = e.venue.map(id => venues.find(_.id == id).map(embed).getOrElse(MsgVenue.Embed.unknown(id))),
      proposals = e.talks.map(id => proposals.find(_.id == id).map(embed(_, users)).getOrElse(MsgProposal.Embed.unknown(id))),
      tags = e.tags,
      published = e.published.isDefined,
      links = Map(
        "drawAttendee" -> req.format(gospeak.web.pages.published.groups.routes.GroupCtrl.eventDrawMeetupAttendee(g.slug, e.slug))),
      publicLink = req.format(gospeak.web.pages.published.groups.routes.GroupCtrl.event(g.slug, e.slug)),
      orgaLink = req.format(gospeak.web.pages.orga.events.routes.EventCtrl.detail(g.slug, e.slug)),
      meetupLink = e.refs.meetup.map(_.link))

  private def msg(g: Group, c: Cfp, p: Proposal, users: Seq[User])(implicit req: BasicReq[AnyContent]): MsgProposal =
    MsgProposal(
      id = p.id,
      title = p.title,
      duration = p.duration,
      description = p.description,
      speakers = p.speakers.map(id => users.find(_.id == id).map(embed).getOrElse(MsgUser.Embed.unknown(id))),
      slides = p.slides,
      video = p.video,
      tags = p.tags,
      publicLink = req.format(gospeak.web.pages.published.groups.routes.GroupCtrl.talk(g.slug, p.id)),
      orgaLink = req.format(gospeak.web.pages.orga.cfps.proposals.routes.ProposalCtrl.detail(g.slug, c.slug, p.id)))

  private def msg(e: ExternalEvent)(implicit req: BasicReq[AnyContent]): MsgExternalEvent =
    MsgExternalEvent(
      name = e.name,
      start = e.start,
      location = e.location,
      twitterAccount = e.twitterAccount,
      twitterHashtag = e.twitterHashtag,
      tags = e.tags,
      publicLink = req.format(gospeak.web.pages.published.events.routes.EventCtrl.detailExt(e.id)))

  private def msg(c: ExternalCfp)(implicit req: BasicReq[AnyContent]): MsgExternalCfp =
    MsgExternalCfp(
      begin = c.begin,
      close = c.close,
      publicLink = req.format(gospeak.web.pages.published.cfps.routes.CfpCtrl.detailExt(c.id)))

  private def embed(u: User): MsgUser.Embed =
    MsgUser.Embed(
      slug = u.slug,
      name = u.name,
      avatar = u.avatar,
      title = u.title,
      company = u.company,
      website = u.website,
      links = u.social,
      public = u.isPublic)

  private def embed(g: Group, c: Cfp)(implicit req: BasicReq[AnyContent]): MsgCfp.Embed =
    MsgCfp.Embed(
      slug = c.slug,
      name = c.name,
      active = c.isActive(req.nowLDT),
      publicLink = req.format(gospeak.web.pages.published.cfps.routes.CfpCtrl.detail(c.slug)),
      orgaLink = req.format(gospeak.web.pages.orga.cfps.routes.CfpCtrl.detail(g.slug, c.slug)))

  private def embed(g: Group, e: Event, venues: Seq[Venue.Full])(implicit req: BasicReq[AnyContent]): MsgEvent.Embed =
    MsgEvent.Embed(
      slug = e.slug,
      name = e.name,
      kind = e.kind,
      start = e.start,
      description = e.description,
      venue = e.venue.map(id => venues.find(_.id == id).map(embed).getOrElse(MsgVenue.Embed.unknown(id))),
      tags = e.tags,
      published = e.published.isDefined,
      links = Map(
        "drawAttendee" -> req.format(gospeak.web.pages.published.groups.routes.GroupCtrl.eventDrawMeetupAttendee(g.slug, e.slug))),
      publicLink = req.format(gospeak.web.pages.published.groups.routes.GroupCtrl.event(g.slug, e.slug)),
      orgaLink = req.format(gospeak.web.pages.orga.events.routes.EventCtrl.detail(g.slug, e.slug)),
      meetupLink = e.refs.meetup.map(_.link))

  private def embed(v: Venue.Full): MsgVenue.Embed =
    MsgVenue.Embed(
      name = v.partner.name,
      logo = v.partner.logo,
      address = v.address,
      description = v.partner.description,
      links = v.partner.social)

  private def embed(p: Proposal.Full, users: Seq[User])(implicit req: BasicReq[AnyContent]): MsgProposal.Embed =
    MsgProposal.Embed(
      id = p.id,
      title = p.title,
      duration = p.duration,
      description = p.description,
      speakers = p.speakers.map(id => users.find(_.id == id).map(embed).getOrElse(MsgUser.Embed.unknown(id))),
      slides = p.slides,
      video = p.video,
      tags = p.tags,
      publicLink = req.format(gospeak.web.pages.published.groups.routes.GroupCtrl.talk(p.group.slug, p.id)),
      orgaLink = req.format(gospeak.web.pages.orga.cfps.proposals.routes.ProposalCtrl.detail(p.group.slug, p.cfp.slug, p.id)))

  private def embed(p: Partner): MsgPartner.Embed =
    MsgPartner.Embed(
      name = p.name,
      slug = p.slug,
      logo = p.logo)

  private def embed(s: Sponsor.Full): MsgSponsor.Embed =
    MsgSponsor.Embed(
      name = s.partner.name,
      logo = s.partner.logo,
      pack = s.pack.name)

}
