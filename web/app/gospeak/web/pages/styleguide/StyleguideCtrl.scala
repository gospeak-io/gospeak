package gospeak.web.pages.styleguide

import java.time.{Instant, LocalDateTime}

import cats.data.NonEmptyList
import cats.effect.IO
import com.mohiva.play.silhouette.api.{LoginInfo, Silhouette}
import com.mohiva.play.silhouette.impl.authenticators.CookieAuthenticator
import gospeak.core.domain._
import gospeak.core.domain.utils.{Constants, Info, SocialAccounts}
import gospeak.core.services.email.EmailSrv
import gospeak.infra.services.AvatarSrv
import gospeak.web.auth.domain.{AuthUser, CookieEnv}
import gospeak.web.domain.Breadcrumb
import gospeak.web.emails.Emails
import gospeak.web.utils.{OrgaReq, UICtrl, UserAwareReq}
import gospeak.web.{AppConf, pages}
import gospeak.libs.scala.Extensions._
import gospeak.libs.scala.domain._
import org.joda.time.DateTime
import play.api.mvc.{Action, AnyContent, ControllerComponents}

import scala.concurrent.duration._

class StyleguideCtrl(cc: ControllerComponents,
                     silhouette: Silhouette[CookieEnv],
                     conf: AppConf,
                     avatarSrv: AvatarSrv) extends UICtrl(cc, silhouette, conf) {
  private val now = Instant.now()
  private val dt = new DateTime()
  private val ldt = LocalDateTime.now()
  private val email = EmailAddress.from("john.doe@mail.com").get
  private val userSlug = User.Slug.from("john-doe").get
  private val user = User(
    id = User.Id.generate(),
    slug = userSlug,
    status = User.Status.Public,
    firstName = "John",
    lastName = "Doe",
    email = email,
    emailValidated = None,
    emailValidationBeforeLogin = false,
    avatar = avatarSrv.getDefault(email, userSlug),
    title = None,
    bio = None,
    mentoring = None,
    company = None,
    location = None,
    phone = None,
    website = None,
    social = SocialAccounts.fromUrls(),
    createdAt = now,
    updatedAt = now)
  private val identity = AuthUser(
    loginInfo = LoginInfo(providerID = "credentials", providerKey = email.value),
    user = user,
    groups = Seq())
  private val authenticator = CookieAuthenticator("cookie", identity.loginInfo, dt, dt.plusMinutes(1), None, None, None)
  private val group = Group(
    id = Group.Id.generate(),
    slug = Group.Slug.from("group-slug").get,
    name = Group.Name("A group"),
    logo = None,
    banner = None,
    contact = Some(EmailAddress.from("contact@gospeak.io").get),
    website = None,
    description = Markdown(
      """This is an **awesome** group, you should come and see us.
        |
        |We do:
        |- beer
        |- pizzas ^^
      """.stripMargin),
    location = None,
    owners = NonEmptyList.of(user.id),
    social = SocialAccounts.fromUrls(),
    tags = Seq("tag").map(Tag(_)),
    status = Group.Status.Active,
    info = Info(user.id, now))
  private val cfp = Cfp(
    id = Cfp.Id.generate(),
    group = group.id,
    slug = Cfp.Slug.from("cfp-slug").get,
    name = Cfp.Name("CFP 2019!!!"),
    begin = None,
    close = None,
    description = Markdown(
      """Submit your best talk to amaze our attendees ;)
        |
        |We choose talks every week so don't wait
      """.stripMargin),
    Seq("Scala", "UX").map(Tag(_)),
    info = Info(user.id, now))
  private val event = Event(
    id = Event.Id.generate(),
    group = group.id,
    cfp = Some(cfp.id),
    slug = Event.Slug.from("event-slug").get,
    name = Event.Name("Best Event in April \\o/"),
    kind = Event.Kind.Meetup,
    start = ldt,
    maxAttendee = Some(100),
    allowRsvp = false,
    description = MustacheMarkdown(""),
    orgaNotes = Event.Notes("", now, user.id),
    venue = None,
    talks = Seq(),
    tags = Seq("tag").map(Tag(_)),
    published = Some(now),
    refs = Event.ExtRefs(),
    info = Info(user.id, now))
  private val talk = Talk(
    id = Talk.Id.generate(),
    slug = Talk.Slug.from("talk-slug").get,
    title = Talk.Title("FP for the win!"),
    duration = 10.minutes,
    status = Talk.Status.Public,
    description = Markdown(
      """Have you heard about FP?
        |
        |It's the next/actual big thing in tech :D
      """.stripMargin),
    message = Markdown(""),
    speakers = NonEmptyList.of(user.id),
    slides = None,
    video = None,
    tags = Seq("tag").map(Tag(_)),
    info = Info(user.id, now))
  private val proposal = Proposal(
    id = Proposal.Id.generate(),
    talk = talk.id,
    cfp = cfp.id,
    event = None,
    title = talk.title,
    duration = talk.duration,
    status = Proposal.Status.Pending,
    description = talk.description,
    message = Markdown(""),
    speakers = talk.speakers,
    slides = talk.slides,
    video = talk.video,
    tags = Seq("tag").map(Tag(_)),
    orgaTags = Seq("orgaTags").map(Tag(_)),
    info = Info(user.id, now))
  private val partner = Partner(
    id = Partner.Id.generate(),
    group = group.id,
    slug = Partner.Slug.from("zeenea").get,
    name = Partner.Name("Zeenea"),
    notes = Markdown(""),
    description = None,
    logo = Constants.Gospeak.logo,
    social = SocialAccounts.fromUrls(),
    info = Info(user.id, now))
  private val place = GMapPlace(
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
  private val contact = Contact(
    id = Contact.Id.generate(),
    partner = partner.id,
    firstName = Contact.FirstName(user.firstName),
    lastName = Contact.LastName(user.lastName),
    email = user.email,
    notes = Markdown(""),
    info = Info(user.id, now))
  private val venue = Venue(
    id = Venue.Id.generate(),
    partner = partner.id,
    contact = Some(contact.id),
    address = place,
    notes = Markdown(""),
    roomSize = None,
    refs = Venue.ExtRefs(
      meetup = None),
    info = Info(user.id, now))
  private val member = Group.Member(
    group = group.id,
    role = Group.Member.Role.Member,
    presentation = None,
    joinedAt = now,
    leavedAt = None,
    user = user)
  private val rsvp = Event.Rsvp(
    event = event.id,
    answer = Event.Rsvp.Answer.Yes,
    answeredAt = now,
    user = user)
  private val comment = Comment(
    id = Comment.Id.generate(),
    kind = Comment.Kind.Proposal,
    answers = None,
    text = "Comment text\nA second line of text\n\nGood job!",
    createdAt = now,
    createdBy = user.id)
  private val userFull = User.Full(user, 0L, 0L, 0L)
  private val groupFull = Group.Full(group, 0L, 0L, 0L)
  private val venueFull = Venue.Full(venue, partner, Some(contact))
  private val proposalFull = Proposal.Full(proposal, cfp, group, talk, Some(event), Some(venueFull), 0L, None, 0L, None, 0L, 0L, 0L, None)
  private val commonProposal = CommonProposal(proposal, talk, group, cfp, Some(event))
  private val eventFull = Event.Full(event, Some(venueFull), Some(cfp), group)
  private val accountValidationRequest = UserRequest.AccountValidationRequest(
    id = UserRequest.Id.generate(),
    email = user.email,
    deadline = now,
    createdAt = now,
    createdBy = user.id,
    acceptedAt = Some(now))
  private val passwordResetRequest = UserRequest.PasswordResetRequest(
    id = UserRequest.Id.generate(),
    email = user.email,
    deadline = now,
    createdAt = now,
    acceptedAt = Some(now))
  private val groupInvite = UserRequest.GroupInvite(
    id = UserRequest.Id.generate(),
    group = group.id,
    email = user.email,
    deadline = now,
    createdAt = now,
    createdBy = user.id,
    accepted = None,
    rejected = None,
    canceled = None)
  private val talkInvite = UserRequest.TalkInvite(
    id = UserRequest.Id.generate(),
    talk = talk.id,
    email = user.email,
    deadline = now,
    createdAt = now,
    createdBy = user.id,
    accepted = None,
    rejected = None,
    canceled = None)
  private val proposalInvite = UserRequest.ProposalInvite(
    id = UserRequest.Id.generate(),
    proposal = proposal.id,
    email = user.email,
    deadline = now,
    createdAt = now,
    createdBy = user.id,
    accepted = None,
    rejected = None,
    canceled = None)

  def index(params: Page.Params): Action[AnyContent] = UserAction { implicit req =>
    implicit val userAware: UserAwareReq[AnyContent] = req.userAware
    implicit val orga: OrgaReq[AnyContent] = req.orga(group)
    IO.pure(Ok(html.styleguide(user, userFull, group, groupFull, cfp, event, eventFull, talk, proposal, proposalFull, commonProposal, params)))
  }

  def embed(): Action[AnyContent] = UserAction { implicit req =>
    val urls = Seq(
      "YouTube" -> "https://youtu.be/9J9ouo-VNao",
      "YouTube" -> "https://www.youtube.com/watch?v=EAf41LZxoM8",
      "YouTube" -> "https://www.youtube.com/watch?v=_TmJ8MuhAdI&feature=youtu.be",
      "YouTube playlist video" -> "https://www.youtube.com/watch?v=aClcNdOqtsE&list=PLs13l-4BLe9cbVS-s9SRa5KScRc6dyXxe&index=3",
      "YouTube playlist" -> "https://www.youtube.com/playlist?list=PLs13l-4BLe9cKAJEZJoh5u1UQMACty7Xi",

      "Vimeo" -> "https://vimeo.com/showcase/6597308/video/374679107",
      "Vimeo showcase" -> "https://vimeo.com/showcase/6597308",

      "Dailymotion" -> "https://www.dailymotion.com/video/x16jxe2",

      "GoogleSlides" -> "https://docs.google.com/presentation/d/1D714wW1VL4mvB3ptGvmuR-ZdtqCZtliyjz4Ui0fQeVo",
      "GoogleSlides" -> "https://docs.google.com/presentation/d/1ktdMyYNZGOywRuqTwU5Op8gdItctatnHkzWuppqSncU/edit?usp=sharing",

      "SlidesDotCom" -> "http://slides.com/krichtof/pourquoi-je-passe-au-business-model-open-source",
      "SlidesDotCom" -> "https://slides.com/leoanesi/deck",
      "SlidesDotCom" -> "https://slid.es/mcmoe/slack-devoxx-pl-2017",
      "SlidesDotCom profil" -> "https://slides.com/antoinetoubhans-1",
      "SlidesDotCom not found" -> "http://slides.com/mickaelandrieu/introduction-to-nightmarejs",

      "SlideShare" -> "http://fr.slideshare.net/GaryMialaret/gagner-aux-tcg-grce-linformatique",
      "SlideShare" -> "http://www.slideshare.net/christopherparola/elcurator-un-exemple-dintrapreneuriat-conduisant-la-cration-dune-entreprise",

      "SpeakerDeck" -> "https://speakerdeck.com/mickaelandrieu/10-minutes-pour-choisir-sa-licence-open-source",
      "SpeakerDeck" -> "https://speakerdeck.com/dwursteisen/rxjava-getting-started",

      "HtmlSlides" -> "http://talks.pixelastic.com/slides/memory-humantalks-2015/#/",
      "HtmlSlides" -> "http://lauterry.github.io/slides-prez-angular/",
      "HtmlSlides" -> "http://pekelman.com/presentations/apidays/",
      "HtmlSlides" -> "https://l-p.github.io/out-of-google/",
      "HtmlSlides" -> "http://jacinthebusson.com/humantalks/index.html",
      "HtmlSlides" -> "https://gitpitch.com/open-chords-charts/elm-europe-2017-talk",
      "HtmlSlides unknown" -> "http://posva.net/slides/dvorak",
      "HtmlSlides unknown" -> "http://files.catwell.info/presentations/2013-02-human-talks-lua/fr.html",
      "HtmlSlides unknown" -> "http://files.catwell.info/presentations/2013-07-human-talks-mobile-perf/",

      "Pdf" -> "http://loic.knuchel.org/blog/wp-content/uploads/2013/11/HumanTalks11_FPhaskell_by_nmassyl.pdf",
      "Pdf not found" -> "http://konexio.eu/press-kit-fr.pdf",

      "Drive pdf" -> "https://drive.google.com/file/d/0B6mG_GOCuoUIWXh0NEZQak1CVUU/view?usp=sharing",
      "Drive pdf" -> "https://drive.google.com/open?id=0B8mCfAkWpUx0ZXNvV19mRDk4YzB5cmJIWFNCbmZYN3JuVkw4",
      "Drive .key" -> "https://drive.google.com/file/d/0B60RywJ46aLOdDEyWW1QS0NrQnM/view",
      "Drive not authorized" -> "https://drive.google.com/file/d/0B9jbd4WmNPaeOFMzczhQSmFHZE0/view",
      "OneDrive" -> "https://onedrive.live.com/view.aspx?resid=E66DEE7C5AA54223!253901&ithint=file%2cpptx&authkey=!AO9InUz3yD6QHvg",
      "OneDrive" -> "https://onedrive.live.com/view.aspx?resid=E66DEE7C5AA54223!42602&ithint=file%2cpptx&app=PowerPoint&authkey=!AFm6uCuoXSDMFMM",
      "Autre" -> "https://www.dropbox.com/sh/5tsxjhj0o250xas/AAC23iu8L7J70GZejrieLcHHa?dl=0",
    )
    IO.pure(Ok(html.styleguideEmbed(urls)))
  }

  def published(id: String): Action[AnyContent] = UserAction { implicit req =>
    implicit val userAware: UserAwareReq[AnyContent] = req.userAware
    val res = id match {
      case "index" => pages.published.html.index()(Breadcrumb(Seq()))
      case "why" => pages.published.html.why()(Breadcrumb(Seq()))
    }
    IO.pure(Ok(res))
  }

  def speaker(id: String): Action[AnyContent] = UserAction { implicit req =>
    ???
  }

  def orga(id: String): Action[AnyContent] = UserAction { implicit req =>
    ???
  }

  def answers(id: String): Action[AnyContent] = UserAction { implicit req =>
    implicit val userAware: UserAwareReq[AnyContent] = req.userAware
    val res = id match {
      case "GroupInvite" => pages.user.html.answerGroupInvite(groupInvite, group, user)(Breadcrumb(Seq()))
      case "TalkInvite" => pages.user.html.answerTalkInvite(talkInvite, talk, user)(Breadcrumb(Seq()))
      case "ProposalInvite" => pages.user.html.answerProposalInvite(proposalInvite, proposal, user)(Breadcrumb(Seq()))
      // case "ProposalCreation" => pages.user.html.answerProposalCreation(proposalCreation, group, cfp, Some(event), user, UserRequestForms.loggedProposalInvite)(Breadcrumb(Seq()))
    }
    IO.pure(Ok(res))
  }

  def emails(id: String): Action[AnyContent] = UserAction { implicit req =>
    implicit val userAware: UserAwareReq[AnyContent] = req.userAware
    implicit val orga: OrgaReq[AnyContent] = req.orga(group)
    val email: EmailSrv.Email = id match {
      case "signup" => Emails.signup(accountValidationRequest, user)
      case "accountValidation" => Emails.accountValidation(accountValidationRequest, user)
      case "forgotPassword" => Emails.forgotPassword(user, passwordResetRequest)
      case "inviteOrgaToGroup" => Emails.inviteOrgaToGroup(groupInvite, Markdown("message body"))
      case "inviteOrgaToGroupCanceled" => Emails.inviteOrgaToGroupCanceled(groupInvite)
      case "inviteOrgaToGroupAccepted" => Emails.inviteOrgaToGroupAccepted(groupInvite, group, user)
      case "inviteOrgaToGroupRejected" => Emails.inviteOrgaToGroupRejected(groupInvite, group, user)
      case "joinGroupAccepted" => Emails.joinGroupAccepted(user)
      case "joinGroupRejected" => Emails.joinGroupRejected(user)
      case "orgaRemovedFromGroup" => Emails.orgaRemovedFromGroup(user)
      case "inviteSpeakerToTalk" => Emails.inviteSpeakerToTalk(talkInvite, talk, Markdown("message body"))
      case "inviteSpeakerToTalkCanceled" => Emails.inviteSpeakerToTalkCanceled(talkInvite, talk)
      case "inviteSpeakerToTalkAccepted" => Emails.inviteSpeakerToTalkAccepted(talkInvite, talk, user)
      case "inviteSpeakerToTalkRejected" => Emails.inviteSpeakerToTalkRejected(talkInvite, talk, user)
      case "speakerRemovedFromTalk" => Emails.speakerRemovedFromTalk(talk, user)
      case "inviteSpeakerToProposal" => Emails.inviteSpeakerToProposal(proposalInvite, cfp, Some(event), proposal, Markdown("message body"))
      case "inviteSpeakerToProposalCanceled" => Emails.inviteSpeakerToProposalCanceled(proposalInvite, proposal)
      case "inviteSpeakerToProposalAccepted" => Emails.inviteSpeakerToProposalAccepted(proposalInvite, proposalFull, user)
      case "inviteSpeakerToProposalRejected" => Emails.inviteSpeakerToProposalRejected(proposalInvite, proposalFull, user)
      case "speakerRemovedFromProposal" => Emails.speakerRemovedFromProposal(proposal, user)
      case "movedFromWaitingListToAttendees" => Emails.movedFromWaitingListToAttendees(group, event, user)
      // case "proposalCreationRequested" => Emails.proposalCreationRequested(group, cfp, Some(event), proposalCreation)
      // case "proposalCreationCanceled" => Emails.proposalCreationCanceled(group, cfp, Some(event), proposalCreation)
      // case "proposalCreationAccepted" => Emails.proposalCreationAccepted(group, cfp, Some(event), proposalCreation, orgaUser)
      // case "proposalCreationRejected" => Emails.proposalCreationRejected(group, cfp, Some(event), proposalCreation, orgaUser)
      case "eventPublished" => Emails.eventPublished(event, Some(venueFull), member)
      case "proposalCommentAddedForSpeaker" => Emails.proposalCommentAddedForSpeaker(cfp, talk, proposal, NonEmptyList.of(user), comment)
      case "proposalCommentAddedForOrga" => Emails.proposalCommentAddedForOrga(group, cfp, proposal, NonEmptyList.of(user), comment)
      case "eventCommentAdded" => Emails.eventCommentAdded(group, event, user, comment)
      case "groupMessage" => Emails.groupMessage(group, EmailAddress.Contact(user.email), "subject", Markdown("message body"), member)
      case "eventMessage" => Emails.eventMessage(event, EmailAddress.Contact(user.email), "subject", Markdown("message body"), rsvp)
    }
    IO.pure(Ok(html.styleguideEmail(email, id)))
  }
}
