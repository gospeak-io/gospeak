package fr.gospeak.web.pages.orga.events

import fr.gospeak.core.domain.utils.TemplateData
import fr.gospeak.core.domain.{Cfp, Event}
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
    "venue" -> optional(venueId),
    "description" -> template[TemplateData.EventInfo],
    "tags" -> tags,
    "refs" -> eventRefs
  )(Event.Data.apply)(Event.Data.unapply))

  val attachCfp: Form[Cfp.Slug] = Form(single("cfp" -> cfpSlug))

  final case class MeetupOptions(publish: Boolean, draft: Boolean)

  final case class PublishOptions(meetup: Option[MeetupOptions])

  object PublishOptions {
    val default = PublishOptions(
      meetup = Some(MeetupOptions(publish = true, draft = true)))
  }

  val publish: Form[PublishOptions] = Form(mapping(
    "meetup" -> optional(mapping(
      "publish" -> boolean,
      "draft" -> boolean
    )(MeetupOptions.apply)(MeetupOptions.unapply)))
  (PublishOptions.apply)(PublishOptions.unapply))
}
