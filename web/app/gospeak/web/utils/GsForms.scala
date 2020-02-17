package gospeak.web.utils

import java.time.LocalDateTime

import cats.data.NonEmptyList
import gospeak.core.domain.Group.Settings
import gospeak.core.domain._
import gospeak.core.domain.utils.{SocialAccounts, TemplateData}
import gospeak.core.services.meetup.domain.MeetupGroup
import gospeak.core.services.slack.domain.SlackCredentials
import gospeak.libs.scala.Crypto.AesSecretKey
import gospeak.libs.scala.Extensions._
import gospeak.libs.scala.domain.MustacheTmpl.MustacheMarkdownTmpl
import gospeak.libs.scala.domain._
import gospeak.web.utils.Mappings._
import play.api.data.Forms._
import play.api.data.{Form, Mapping}

object GsForms {

  /**
   * Auth forms
   */

  final case class SignupData(slug: User.Slug, firstName: String, lastName: String, email: EmailAddress, password: Secret, rememberMe: Boolean) {
    def data(avatar: Avatar): User.Data = User.Data(slug, User.Status.Public, firstName, lastName, email, avatar, None, None, None, None, None, None, SocialAccounts.fromUrls())
  }

  private val signupMapping: Mapping[SignupData] = mapping(
    "slug" -> userSlug,
    "first-name" -> text(1, Values.maxLength.title),
    "last-name" -> text(1, Values.maxLength.title),
    "email" -> emailAddress,
    "password" -> password,
    "rememberMe" -> boolean
  )(SignupData.apply)(SignupData.unapply)
  val signup: Form[SignupData] = Form(signupMapping)

  final case class LoginData(email: EmailAddress, password: Secret, rememberMe: Boolean)

  private val loginMapping: Mapping[LoginData] = mapping(
    "email" -> emailAddress,
    "password" -> secret,
    "rememberMe" -> boolean
  )(LoginData.apply)(LoginData.unapply)
  val login: Form[LoginData] = Form(loginMapping)

  final case class ForgotPasswordData(email: EmailAddress)

  val forgotPassword: Form[ForgotPasswordData] = Form(mapping(
    "email" -> emailAddress
  )(ForgotPasswordData.apply)(ForgotPasswordData.unapply))

  final case class ResetPasswordData(password: Secret, rememberMe: Boolean)

  val resetPassword: Form[ResetPasswordData] = Form(mapping(
    "password" -> password,
    "rememberMe" -> boolean
  )(ResetPasswordData.apply)(ResetPasswordData.unapply))


  /**
   * Generic forms
   */

  val embed: Form[Url] = Form(single(
    "url" -> url))

  final case class Invite(email: EmailAddress, message: Markdown)

  val invite: Form[Invite] = Form(mapping(
    "email" -> emailAddress,
    "message" -> markdown
  )(Invite.apply)(Invite.unapply))

  val comment: Form[Comment.Data] = Form(mapping(
    "answers" -> optional(commentId),
    "text" -> nonEmptyText
  )(Comment.Data.apply)(Comment.Data.unapply))


  /**
   * Models forms
   */

  val user: Form[User.Data] = Form(mapping(
    "slug" -> userSlug,
    "status" -> userStatus,
    "first-name" -> text(1, Values.maxLength.title),
    "last-name" -> text(1, Values.maxLength.title),
    "email" -> emailAddress,
    "avatar" -> avatar,
    "title" -> optional(text),
    "bio" -> optional(markdown),
    "company" -> optional(text),
    "location" -> optional(text),
    "phone" -> optional(text),
    "website" -> optional(url),
    "social" -> socialAccounts
  )(User.Data.apply)(User.Data.unapply))

  val group: Form[Group.Data] = Form(mapping(
    "slug" -> groupSlug,
    "name" -> groupName,
    "logo" -> optional(logo),
    "banner" -> optional(banner),
    "contact" -> optional(emailAddress),
    "website" -> optional(url),
    "description" -> markdown,
    "location" -> optional(gMapPlace),
    "social" -> socialAccounts,
    "tags" -> tags
  )(Group.Data.apply)(Group.Data.unapply))

  final case class GroupContact(from: EmailAddress,
                                subject: String,
                                content: Markdown)

  val groupContact: Form[GroupContact] = Form(mapping(
    "from" -> emailAddress,
    "subject" -> nonEmptyText,
    "content" -> markdown
  )(GroupContact.apply)(GroupContact.unapply))

  object GroupAccount {

    final case class Meetup(group: MeetupGroup.Slug)

  }

  val groupAccountMeetup: Form[GroupAccount.Meetup] = Form(mapping(
    "group" -> meetupGroupSlug
  )(GroupAccount.Meetup.apply)(GroupAccount.Meetup.unapply))

  def groupAccountSlack(key: AesSecretKey): Form[SlackCredentials] = Form(mapping(
    "token" -> slackToken(key),
    "name" -> nonEmptyText,
    "avatar" -> optional(avatar)
  )(SlackCredentials.apply)(SlackCredentials.unapply))

  final case class GroupAction(trigger: Settings.Action.Trigger, action: Settings.Action)

  val groupAction: Form[GroupAction] = Form(mapping(
    "trigger" -> groupSettingsEvent,
    "action" -> groupSettingsAction
  )(GroupAction.apply)(GroupAction.unapply))

  final case class GroupEventTemplateItem(id: String, template: MustacheMarkdownTmpl[TemplateData.EventInfo])

  val groupEventTemplateItem: Form[GroupEventTemplateItem] = Form(mapping(
    "id" -> nonEmptyText,
    "template" -> template[TemplateData.EventInfo]
  )(GroupEventTemplateItem.apply)(GroupEventTemplateItem.unapply))

  val event: Form[Event.Data] = Form(mapping(
    "cfp" -> optional(cfpId),
    "slug" -> eventSlug,
    "name" -> eventName,
    "kind" -> eventKind,
    "start" -> myLocalDateTime,
    "max-attendee" -> optional(number),
    "allow-rsvp" -> boolean,
    "venue" -> optional(venueId),
    "description" -> template[TemplateData.EventInfo],
    "tags" -> tags,
    "refs" -> eventRefs
  )(Event.Data.apply)(Event.Data.unapply))

  val eventNotes: Form[String] = Form(single("notes" -> text))
  val eventCfp: Form[Cfp.Id] = Form(single("cfp" -> cfpId))

  final case class PublishOptions(notifyMembers: Boolean, meetup: Option[PublishOptions.Meetup])

  object PublishOptions {

    final case class Meetup(publish: Boolean, draft: Boolean)

    val default: PublishOptions = PublishOptions(
      notifyMembers = true,
      meetup = Some(Meetup(publish = true, draft = true)))
  }

  val eventPublish: Form[PublishOptions] = Form(mapping(
    "notifyMembers" -> boolean,
    "meetup" -> optional(mapping(
      "publish" -> boolean,
      "draft" -> boolean
    )(PublishOptions.Meetup.apply)(PublishOptions.Meetup.unapply)))
  (PublishOptions.apply)(PublishOptions.unapply))

  final case class EventContact(from: EmailAddress,
                                to: EventContact.Recipient,
                                subject: String,
                                content: Markdown)

  object EventContact {

    sealed abstract class Recipient(val description: String,
                                    val answers: NonEmptyList[Event.Rsvp.Answer]) extends StringEnum {
      override def value: String = toString
    }

    object Recipient extends EnumBuilder[Recipient]("GsForms.EventContact.Recipient") {

      case object Yes extends Recipient("Members that have a reservation", NonEmptyList.of(Event.Rsvp.Answer.Yes))

      case object Wait extends Recipient("Members that are on waiting list", NonEmptyList.of(Event.Rsvp.Answer.Wait))

      case object YesAndWait extends Recipient("Members that answered Yes (with a reservation or not)", NonEmptyList.of(Event.Rsvp.Answer.Yes, Event.Rsvp.Answer.Wait))

      case object No extends Recipient("Members that answered No", NonEmptyList.of(Event.Rsvp.Answer.No))

      override val all: Seq[Recipient] = Seq(Yes, Wait, YesAndWait, No)
    }

  }

  val eventContact: Form[EventContact] = Form(mapping(
    "from" -> emailAddress,
    "to" -> nonEmptyText.verifying(EventContact.Recipient.from(_).isRight).transform[EventContact.Recipient](EventContact.Recipient.from(_).get, _.value),
    "subject" -> nonEmptyText,
    "content" -> markdown
  )(EventContact.apply)(EventContact.unapply))

  val cfp: Form[Cfp.Data] = Form(mapping(
    "slug" -> cfpSlug,
    "name" -> cfpName,
    "start" -> optional(myLocalDateTime),
    "end" -> optional(myLocalDateTime),
    "description" -> markdown,
    "tags" -> tags
  )(Cfp.Data.apply)(Cfp.Data.unapply)
    .verifying("Start of Cfp should be anterior to its end", isStartBeforeEnd _))

  private def isStartBeforeEnd(data: Cfp.Data): Boolean = (data.begin, data.close) match {
    case (Some(start), Some(end)) if start.isAfter(end) => false
    case _ => true
  }

  private val talkMapping: Mapping[Talk.Data] = mapping(
    "slug" -> talkSlug,
    "title" -> talkTitle,
    "duration" -> duration,
    "description" -> markdown,
    "message" -> markdown,
    "slides" -> optional(slides),
    "video" -> optional(video),
    "tags" -> tags
  )(Talk.Data.apply)(Talk.Data.unapply)
  val talk: Form[Talk.Data] = Form(talkMapping)

  sealed trait TalkAndProposalData {
    val talk: Talk.Data

    def proposal: Proposal.Data = Proposal.Data(talk)
  }

  final case class TalkLogged(talk: Talk.Data) extends TalkAndProposalData

  val talkLogged: Form[TalkLogged] = Form(mapping(
    "talk" -> talkMapping
  )(TalkLogged.apply)(TalkLogged.unapply))

  final case class TalkSignup(talk: Talk.Data, user: SignupData) extends TalkAndProposalData

  val talkSignup: Form[TalkSignup] = Form(mapping(
    "talk" -> talkMapping,
    "user" -> signupMapping
  )(TalkSignup.apply)(TalkSignup.unapply))

  final case class TalkLogin(talk: Talk.Data, user: LoginData) extends TalkAndProposalData

  val talkLogin: Form[TalkLogin] = Form(mapping(
    "talk" -> talkMapping,
    "user" -> loginMapping
  )(TalkLogin.apply)(TalkLogin.unapply))

  val proposal: Form[Proposal.Data] = Form(mapping(
    "title" -> talkTitle,
    "duration" -> duration,
    "description" -> markdown,
    "message" -> markdown,
    "slides" -> optional(slides),
    "video" -> optional(video),
    "tags" -> tags
  )(Proposal.Data.apply)(Proposal.Data.unapply))

  val proposalOrga: Form[Proposal.DataOrga] = Form(mapping(
    "title" -> talkTitle,
    "duration" -> duration,
    "description" -> markdown,
    "slides" -> optional(slides),
    "video" -> optional(video),
    "tags" -> tags,
    "orgaTags" -> tags
  )(Proposal.DataOrga.apply)(Proposal.DataOrga.unapply))

  val partner: Form[Partner.Data] = Form(mapping(
    "slug" -> partnerSlug,
    "name" -> partnerName,
    "notes" -> markdown,
    "description" -> optional(markdown),
    "logo" -> logo,
    "social" -> socialAccounts
  )(Partner.Data.apply)(Partner.Data.unapply))

  val venue: Form[Venue.Data] = Form(mapping(
    "contact" -> optional(contactId),
    "address" -> gMapPlace,
    "notes" -> markdown,
    "roomSize" -> optional(number),
    "refs" -> venueRefs
  )(Venue.Data.apply)(Venue.Data.unapply))

  val contact: Form[Contact.Data] = Form(mapping(
    "partner" -> partnerId,
    "first_name" -> contactFirstName,
    "last_name" -> contactLastName,
    "email" -> emailAddress,
    "notes" -> markdown
  )(Contact.Data.apply)(Contact.Data.unapply))

  final case class VenueWithPartnerData(slug: Partner.Slug,
                                        name: Partner.Name,
                                        logo: Logo,
                                        address: GMapPlace) {
    def toPartner: Partner.Data = Partner.Data(
      slug = slug,
      name = name,
      notes = Markdown(""),
      description = None,
      logo = logo,
      social = SocialAccounts.fromUrls())

    def toVenue: Venue.Data = Venue.Data(
      contact = None,
      address = address,
      notes = Markdown(""),
      roomSize = None,
      refs = Venue.ExtRefs())
  }

  val venueWithPartner: Form[VenueWithPartnerData] = Form(mapping(
    "slug" -> partnerSlug,
    "name" -> partnerName,
    "logo" -> logo,
    "address" -> gMapPlace
  )(VenueWithPartnerData.apply)(VenueWithPartnerData.unapply))

  val sponsorPack: Form[SponsorPack.Data] = Form(mapping(
    "slug" -> sponsorPackSlug,
    "name" -> sponsorPackName,
    "description" -> markdown,
    "price" -> price,
    "duration" -> period
  )(SponsorPack.Data.apply)(SponsorPack.Data.unapply))

  val sponsor: Form[Sponsor.Data] = Form(mapping(
    "partner" -> partnerId,
    "pack" -> sponsorPackId,
    "contact" -> optional(contactId),
    "start" -> localDate(localDateFormat),
    "finish" -> localDate(localDateFormat),
    "paid" -> optional(localDate(localDateFormat)),
    "price" -> price
  )(Sponsor.Data.apply)(Sponsor.Data.unapply))

  private val externalEventMapping = mapping(
    "name" -> eventName,
    "kind" -> eventKind,
    "logo" -> optional(logo),
    "description" -> markdown,
    "start" -> optional(localDate(localDateFormat).transform[LocalDateTime](_.atTime(0, 0), _.toLocalDate)),
    "finish" -> optional(localDate(localDateFormat).transform[LocalDateTime](_.atTime(23, 59), _.toLocalDate)),
    "location" -> optional(gMapPlace),
    "url" -> optional(url),
    "tickets" -> optional(url),
    "videos" -> optional(url),
    "twitterAccount" -> optional(twitterAccount),
    "twitterHashtag" -> optional(twitterHashtag),
    "tags" -> tags
  )(ExternalEvent.Data.apply)(ExternalEvent.Data.unapply)
  val externalEvent: Form[ExternalEvent.Data] = Form(externalEventMapping)

  private val externalCfpMapping = mapping(
    "description" -> markdown,
    "begin" -> optional(localDate(localDateFormat).transform[LocalDateTime](_.atTime(0, 0), _.toLocalDate)),
    "close" -> optional(localDate(localDateFormat).transform[LocalDateTime](_.atTime(23, 59), _.toLocalDate)),
    "url" -> url
  )(ExternalCfp.Data.apply)(ExternalCfp.Data.unapply)
  val externalCfp: Form[ExternalCfp.Data] = Form(externalCfpMapping)

  final case class ExternalCfpAndEvent(cfp: ExternalCfp.Data, event: ExternalEvent.Data)

  val externalCfpAndEvent: Form[ExternalCfpAndEvent] = Form(mapping(
    "cfp" -> externalCfpMapping,
    "event" -> externalEventMapping
  )(ExternalCfpAndEvent.apply)(ExternalCfpAndEvent.unapply))

  private val externalProposalMapping: Mapping[ExternalProposal.Data] = mapping(
    "status" -> proposalStatus,
    "title" -> talkTitle,
    "duration" -> duration,
    "description" -> markdown,
    "message" -> markdown,
    "slides" -> optional(slides),
    "video" -> optional(video),
    "url" -> optional(url),
    "tags" -> tags
  )(ExternalProposal.Data.apply)(ExternalProposal.Data.unapply)
  val externalProposal: Form[ExternalProposal.Data] = Form(externalProposalMapping)
}
