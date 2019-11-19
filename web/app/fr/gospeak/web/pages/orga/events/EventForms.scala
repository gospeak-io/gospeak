package fr.gospeak.web.pages.orga.events

import cats.data.NonEmptyList
import fr.gospeak.core.domain.UserRequest.ProposalCreation
import fr.gospeak.core.domain.utils.TemplateData
import fr.gospeak.core.domain.{Cfp, Event}
import fr.gospeak.libs.scalautils.Extensions._
import fr.gospeak.libs.scalautils.domain.{EmailAddress, EnumBuilder, Markdown, StringEnum}
import fr.gospeak.web.utils.Mappings._
import play.api.data.Forms._
import play.api.data._

object EventForms {
  val create: Form[Event.Data] = Form(mapping(
    "cfp" -> optional(cfpId),
    "slug" -> eventSlug,
    "name" -> eventName,
    "start" -> myLocalDateTime,
    "max-attendee" -> optional(number),
    "allow-rsvp" -> boolean,
    "venue" -> optional(venueId),
    "description" -> template[TemplateData.EventInfo],
    "tags" -> tags,
    "refs" -> eventRefs
  )(Event.Data.apply)(Event.Data.unapply))

  val notes: Form[String] = Form(single("notes" -> text))

  val cfp: Form[Cfp.Slug] = Form(single("cfp" -> cfpSlug))

  final case class MeetupOptions(publish: Boolean, draft: Boolean)

  final case class PublishOptions(notifyMembers: Boolean,
                                  meetup: Option[MeetupOptions])

  object PublishOptions {
    val default = PublishOptions(
      notifyMembers = true,
      meetup = Some(MeetupOptions(publish = true, draft = true)))
  }

  val publish: Form[PublishOptions] = Form(mapping(
    "notifyMembers" -> boolean,
    "meetup" -> optional(mapping(
      "publish" -> boolean,
      "draft" -> boolean
    )(MeetupOptions.apply)(MeetupOptions.unapply)))
  (PublishOptions.apply)(PublishOptions.unapply))

  sealed abstract class To(val description: String,
                           val answers: NonEmptyList[Event.Rsvp.Answer]) extends StringEnum {
    override def value: String = toString
  }

  final case class ProposalCreationData(cfp: Cfp.Id,
                                        event: Option[Event.Id],
                                        email: EmailAddress,
                                        payload: ProposalCreation.Payload)

  val submissionMapping: Mapping[ProposalCreation.Submission] = mapping(
    "slug" -> talkSlug,
    "title" -> talkTitle,
    "duration" -> duration,
    "description" -> markdown,
    "tags" -> tags
  )(ProposalCreation.Submission.apply)(ProposalCreation.Submission.unapply)
  val proposalCreation: Form[ProposalCreationData] = Form(mapping(
    "cfp" -> cfpId,
    "event" -> optional(eventId),
    "email" -> emailAddress,
    "payload" -> mapping(
      "account" -> mapping(
        "slug" -> userSlug,
        "firstName" -> nonEmptyText,
        "lastName" -> nonEmptyText
      )(ProposalCreation.Account.apply)(ProposalCreation.Account.unapply),
      "submission" -> submissionMapping
    )(ProposalCreation.Payload.apply)(ProposalCreation.Payload.unapply))
  (ProposalCreationData.apply)(ProposalCreationData.unapply))

  object To extends EnumBuilder[To]("EventForms.To") {

    case object Yes extends To("Members that have a reservation", NonEmptyList.of(Event.Rsvp.Answer.Yes))

    case object Wait extends To("Members that are on waiting list", NonEmptyList.of(Event.Rsvp.Answer.Wait))

    case object YesAndWait extends To("Members that answered Yes (with a reservation or not)", NonEmptyList.of(Event.Rsvp.Answer.Yes, Event.Rsvp.Answer.Wait))

    case object No extends To("Members that answered No", NonEmptyList.of(Event.Rsvp.Answer.No))

    override val all: Seq[To] = Seq(Yes, Wait, YesAndWait, No)
  }

  final case class ContactAttendees(from: EmailAddress,
                                    to: To,
                                    subject: String,
                                    content: Markdown)

  val contactAttendees: Form[ContactAttendees] = Form(mapping(
    "from" -> emailAddress,
    "to" -> nonEmptyText.verifying(To.from(_).isRight).transform[To](To.from(_).get, _.value),
    "subject" -> nonEmptyText,
    "content" -> markdown
  )(ContactAttendees.apply)(ContactAttendees.unapply))
}
