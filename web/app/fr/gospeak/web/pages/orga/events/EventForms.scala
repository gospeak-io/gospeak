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
    "start" -> localDateTime,
    "venue" -> optional(venueId),
    "description" -> template[TemplateData.EventInfo],
    "tags" -> tags
  )(Event.Data.apply)(Event.Data.unapply))

  val attachCfp: Form[Cfp.Slug] = Form(single("cfp" -> cfpSlug))
}
