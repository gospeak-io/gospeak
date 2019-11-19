package fr.gospeak.web.pages.styleguide

import java.time.{Instant, LocalDateTime}

import cats.data.NonEmptyList
import cats.effect.IO
import com.mohiva.play.silhouette.api.Silhouette
import fr.gospeak.core.ApplicationConf
import fr.gospeak.core.domain._
import fr.gospeak.core.domain.utils.{Constants, Info, SocialAccounts}
import fr.gospeak.infra.services.{EmailSrv, GravatarSrv}
import fr.gospeak.libs.scalautils.Extensions._
import fr.gospeak.libs.scalautils.domain.MustacheTmpl.MustacheMarkdownTmpl
import fr.gospeak.libs.scalautils.domain.{EmailAddress, GMapPlace, Geo, Markdown, Page, Tag, Url}
import fr.gospeak.web._
import fr.gospeak.web.auth.domain.CookieEnv
import fr.gospeak.web.domain.Breadcrumb
import fr.gospeak.web.emails.Emails
import fr.gospeak.web.pages.user.UserRequestForms
import fr.gospeak.web.utils.{UICtrl, UserAwareReq}
import play.api.mvc.{Action, AnyContent, ControllerComponents}

import scala.concurrent.duration._

class StyleguideCtrl(cc: ControllerComponents,
                     silhouette: Silhouette[CookieEnv],
                     env: ApplicationConf.Env) extends UICtrl(cc, silhouette, env) {
  private val now = Instant.now()
  private val ldt = LocalDateTime.now()
  private val user = User(
    id = User.Id.generate(),
    slug = User.Slug.from("john-doe").get,
    firstName = "John",
    lastName = "Doe",
    email = EmailAddress.from("john.doe@mail.com").get,
    emailValidated = Some(now),
    avatar = GravatarSrv.getAvatar(EmailAddress.from("john.doe@mail.com").get),
    profile = User.emptyProfile,
    created = now,
    updated = now)
  private val orgaUser = User(
    id = User.Id.generate(),
    slug = User.Slug.from("orga").get,
    firstName = "Orga",
    lastName = "User",
    email = EmailAddress.from("orga@mail.com").get,
    emailValidated = Some(now),
    avatar = GravatarSrv.getAvatar(EmailAddress.from("orga@mail.com").get),
    profile = User.emptyProfile,
    created = now,
    updated = now)
  private val speakerUser = User(
    id = User.Id.generate(),
    slug = User.Slug.from("speaker").get,
    firstName = "Speaker",
    lastName = "User",
    email = EmailAddress.from("speaker@mail.com").get,
    emailValidated = Some(now),
    avatar = GravatarSrv.getAvatar(EmailAddress.from("speaker@mail.com").get),
    profile = User.emptyProfile,
    created = now,
    updated = now)
  private val group = Group(
    id = Group.Id.generate(),
    slug = Group.Slug.from("gospeak").get,
    name = Group.Name("Gospeak mob"),
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
    social = SocialAccounts(),
    tags = Seq("Tech").map(Tag(_)),
    status = Group.Status.Active,
    info = Info(user.id, now))
  private val cfp = Cfp(
    id = Cfp.Id.generate(),
    group = group.id,
    slug = Cfp.Slug.from("worst-code-awards").get,
    name = Cfp.Name("Worst code awards"),
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
    slug = Event.Slug.from("bad-code-showcase-november").get,
    name = Event.Name("Bad code showcase November"),
    start = ldt,
    maxAttendee = Some(100),
    allowRsvp = true,
    description = MustacheMarkdownTmpl(""),
    orgaNotes = Event.Notes("", now, user.id),
    venue = None,
    talks = Seq(),
    tags = Seq("Scala").map(Tag(_)),
    published = Some(now),
    refs = Event.ExtRefs(),
    info = Info(user.id, now))
  private val talk = Talk(
    id = Talk.Id.generate(),
    slug = Talk.Slug.from("implicit-magic-you-will-never-find").get,
    title = Talk.Title("Implicit magic, you will never find"),
    duration = 10.minutes,
    status = Talk.Status.Public,
    description = Markdown(
      """Do you know implicits? They are awesome.
        |
        |The allow magic, but also crazy things you can't imagine
      """.stripMargin),
    speakers = NonEmptyList.of(user.id),
    slides = None,
    video = None,
    tags = Seq("Scala", "Implicits").map(Tag(_)),
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
    tags = talk.tags,
    info = Info(user.id, now))
  private val partner = Partner(
    id = Partner.Id.generate(),
    group = group.id,
    slug = Partner.Slug.from("zeenea").get,
    name = Partner.Name("Zeenea"),
    notes = Markdown(""),
    description = None,
    logo = Url.from("https://gospeak.io").get,
    twitter = None,
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
    utcOffset = 60,
    timezone = Constants.defaultZoneId)
  private val contact = Contact(
    id = Contact.Id.generate(),
    partner = partner.id,
    firstName = Contact.FirstName(user.firstName),
    lastName = Contact.LastName(user.lastName),
    email = user.email,
    description = Markdown(""),
    info = Info(user.id, now))
  private val venue = Venue(
    id = Venue.Id.generate(),
    partner = partner.id,
    contact = Some(contact.id),
    address = place,
    description = Markdown(""),
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
  private val proposalFull = Proposal.Full(proposal, cfp, group, talk, Some(event), 0L, 0L, 0L)
  private val venueFull = Venue.Full(venue, partner, Some(contact))
  private val accountValidationRequest = UserRequest.AccountValidationRequest(
    id = UserRequest.Id.generate(),
    email = user.email,
    deadline = now,
    created = now,
    createdBy = user.id,
    accepted = Some(now))
  private val passwordResetRequest = UserRequest.PasswordResetRequest(
    id = UserRequest.Id.generate(),
    email = user.email,
    deadline = now,
    created = now,
    accepted = Some(now))
  private val groupInvite = UserRequest.GroupInvite(
    id = UserRequest.Id.generate(),
    group = group.id,
    email = user.email,
    created = now,
    createdBy = user.id,
    accepted = None,
    rejected = None,
    canceled = None)
  private val talkInvite = UserRequest.TalkInvite(
    id = UserRequest.Id.generate(),
    talk = talk.id,
    email = user.email,
    created = now,
    createdBy = user.id,
    accepted = None,
    rejected = None,
    canceled = None)
  private val proposalInvite = UserRequest.ProposalInvite(
    id = UserRequest.Id.generate(),
    proposal = proposal.id,
    email = user.email,
    created = now,
    createdBy = user.id,
    accepted = None,
    rejected = None,
    canceled = None)
  private val proposalCreation = UserRequest.ProposalCreation(
    id = UserRequest.Id.generate(),
    cfp = cfp.id,
    event = Some(event.id),
    email = user.email,
    payload = UserRequest.ProposalCreation.Payload(
      account = UserRequest.ProposalCreation.Account(
        slug = user.slug,
        firstName = user.firstName,
        lastName = user.lastName),
      submission = UserRequest.ProposalCreation.Submission(
        slug = talk.slug,
        title = talk.title,
        duration = talk.duration,
        description = talk.description,
        tags = talk.tags)),
    created = now,
    createdBy = user.id,
    accepted = None,
    rejected = None,
    canceled = None)

  def index(params: Page.Params): Action[AnyContent] = SecuredActionIO { implicit req =>
    implicit val userAware: UserAwareReq[AnyContent] = req.userAware
    IO.pure(Ok(html.styleguide(user, group, cfp, event, talk, proposal, proposalFull, params)))
  }

  def published(id: String): Action[AnyContent] = SecuredActionIO { implicit req =>
    implicit val userAware: UserAwareReq[AnyContent] = req.userAware
    val res = id match {
      case "index" => pages.published.html.index()(Breadcrumb(Seq()))
      case "why" => pages.published.html.why()(Breadcrumb(Seq()))
    }
    IO.pure(Ok(res))
  }

  def speaker(id: String): Action[AnyContent] = SecuredActionIO { implicit req =>
    ???
  }

  def orga(id: String): Action[AnyContent] = SecuredActionIO { implicit req =>
    ???
  }

  def answers(id: String): Action[AnyContent] = SecuredActionIO { implicit req =>
    implicit val userAware: UserAwareReq[AnyContent] = req.userAware
    val res = id match {
      case "GroupInvite" => pages.user.html.answerGroupInvite(groupInvite, group, user)(Breadcrumb(Seq()))
      case "TalkInvite" => pages.user.html.answerTalkInvite(talkInvite, talk, user)(Breadcrumb(Seq()))
      case "ProposalInvite" => pages.user.html.answerProposalInvite(proposalInvite, proposal, user)(Breadcrumb(Seq()))
      case "ProposalCreation" => pages.user.html.answerProposalCreation(proposalCreation, group, cfp, Some(event), user, UserRequestForms.loggedProposalInvite)(Breadcrumb(Seq()))
    }
    IO.pure(Ok(res))
  }

  def emails(id: String): Action[AnyContent] = SecuredActionIO { implicit req =>
    implicit val userAware: UserAwareReq[AnyContent] = req.userAware
    val email: EmailSrv.Email = id match {
      case "signup" => Emails.signup(accountValidationRequest)
      case "accountValidation" => Emails.accountValidation(accountValidationRequest)
      case "forgotPassword" => Emails.forgotPassword(user, passwordResetRequest)
      case "inviteOrgaToGroup" => Emails.inviteOrgaToGroup(groupInvite, group)
      case "inviteOrgaToGroupCanceled" => Emails.inviteOrgaToGroupCanceled(groupInvite, group)
      case "inviteOrgaToGroupAccepted" => Emails.inviteOrgaToGroupAccepted(groupInvite, group, orgaUser)
      case "inviteOrgaToGroupRejected" => Emails.inviteOrgaToGroupRejected(groupInvite, group, orgaUser)
      case "joinGroupAccepted" => Emails.joinGroupAccepted(user, group)
      case "joinGroupRejected" => Emails.joinGroupRejected(user, group)
      case "orgaRemovedFromGroup" => Emails.orgaRemovedFromGroup(group, user)
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
      case "proposalCreationRequested" => Emails.proposalCreationRequested(group, cfp, Some(event), proposalCreation)
      case "proposalCreationCanceled" => Emails.proposalCreationCanceled(group, cfp, Some(event), proposalCreation)
      case "proposalCreationAccepted" => Emails.proposalCreationAccepted(group, cfp, Some(event), proposalCreation, orgaUser)
      case "proposalCreationRejected" => Emails.proposalCreationRejected(group, cfp, Some(event), proposalCreation, orgaUser)
      case "eventPublished" => Emails.eventPublished(group, event, Some(venueFull), member)
      case "groupMessage" => Emails.groupMessage(group, EmailAddress.Contact(user.email), "subject", Markdown("message body"), member)
      case "eventMessage" => Emails.eventMessage(group, event, EmailAddress.Contact(user.email), "subject", Markdown("message body"), rsvp)
    }
    IO.pure(Ok(html.styleguideEmail(email, id)))
  }
}
