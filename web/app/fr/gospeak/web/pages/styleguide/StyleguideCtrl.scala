package fr.gospeak.web.pages.styleguide

import java.time.{Instant, LocalDateTime}

import cats.data.NonEmptyList
import cats.effect.IO
import com.mohiva.play.silhouette.api.{LoginInfo, Silhouette}
import com.mohiva.play.silhouette.impl.authenticators.CookieAuthenticator
import fr.gospeak.core.domain._
import fr.gospeak.core.domain.utils.{Info, SocialAccounts}
import fr.gospeak.core.services.email.EmailSrv
import fr.gospeak.infra.services.AvatarSrv
import fr.gospeak.libs.scalautils.Extensions._
import fr.gospeak.libs.scalautils.domain.MustacheTmpl.MustacheMarkdownTmpl
import fr.gospeak.libs.scalautils.domain._
import fr.gospeak.web.auth.domain.{AuthUser, CookieEnv}
import fr.gospeak.web.domain.Breadcrumb
import fr.gospeak.web.emails.Emails
import fr.gospeak.web.utils.{OrgaReq, UICtrl, UserAwareReq}
import fr.gospeak.web.{AppConf, pages}
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
    bio = None,
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
    start = ldt,
    maxAttendee = Some(100),
    allowRsvp = false,
    description = MustacheMarkdownTmpl(""),
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
    logo = Url.from("https://gospeak.io").map(Logo).get,
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
  private val groupFull = Group.Full(group, 0L, 0L, 0L)
  private val proposalFull = Proposal.Full(proposal, cfp, group, talk, Some(event), 0L, 0L, 0L)
  private val venueFull = Venue.Full(venue, partner, Some(contact))
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
    createdAt = now,
    createdBy = user.id,
    accepted = None,
    rejected = None,
    canceled = None)
  private val talkInvite = UserRequest.TalkInvite(
    id = UserRequest.Id.generate(),
    talk = talk.id,
    email = user.email,
    createdAt = now,
    createdBy = user.id,
    accepted = None,
    rejected = None,
    canceled = None)
  private val proposalInvite = UserRequest.ProposalInvite(
    id = UserRequest.Id.generate(),
    proposal = proposal.id,
    email = user.email,
    createdAt = now,
    createdBy = user.id,
    accepted = None,
    rejected = None,
    canceled = None)

  def index(params: Page.Params): Action[AnyContent] = UserAction { implicit req =>
    implicit val userAware: UserAwareReq[AnyContent] = req.userAware
    implicit val orga: OrgaReq[AnyContent] = req.orga(group)
    IO.pure(Ok(html.styleguide(user, group, groupFull, cfp, event, eventFull, talk, proposal, proposalFull, params)))
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
      case "inviteOrgaToGroup" => Emails.inviteOrgaToGroup(groupInvite)
      case "inviteOrgaToGroupCanceled" => Emails.inviteOrgaToGroupCanceled(groupInvite)
      case "inviteOrgaToGroupAccepted" => Emails.inviteOrgaToGroupAccepted(groupInvite, group, user)
      case "inviteOrgaToGroupRejected" => Emails.inviteOrgaToGroupRejected(groupInvite, group, user)
      case "joinGroupAccepted" => Emails.joinGroupAccepted(user)
      case "joinGroupRejected" => Emails.joinGroupRejected(user)
      case "orgaRemovedFromGroup" => Emails.orgaRemovedFromGroup(user)
      case "inviteSpeakerToTalk" => Emails.inviteSpeakerToTalk(talkInvite, talk)
      case "inviteSpeakerToTalkCanceled" => Emails.inviteSpeakerToTalkCanceled(talkInvite, talk)
      case "inviteSpeakerToTalkAccepted" => Emails.inviteSpeakerToTalkAccepted(talkInvite, talk, user)
      case "inviteSpeakerToTalkRejected" => Emails.inviteSpeakerToTalkRejected(talkInvite, talk, user)
      case "speakerRemovedFromTalk" => Emails.speakerRemovedFromTalk(talk, user)
      case "inviteSpeakerToProposal" => Emails.inviteSpeakerToProposal(proposalInvite, proposal)
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
      case "proposalCommentAddedForSpeaker" => Emails.proposalCommentAddedForSpeaker(cfp, talk, proposal, user, comment)
      case "proposalCommentAddedForOrga" => Emails.proposalCommentAddedForOrga(group, cfp, proposal, user, comment)
      case "eventCommentAdded" => Emails.eventCommentAdded(group, event, user, comment)
      case "groupMessage" => Emails.groupMessage(group, EmailAddress.Contact(user.email), "subject", Markdown("message body"), member)
      case "eventMessage" => Emails.eventMessage(event, EmailAddress.Contact(user.email), "subject", Markdown("message body"), rsvp)
    }
    IO.pure(Ok(html.styleguideEmail(email, id)))
  }
}
