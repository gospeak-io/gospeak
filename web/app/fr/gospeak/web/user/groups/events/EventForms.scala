package fr.gospeak.web.user.groups.events

import fr.gospeak.core.domain.Event
import fr.gospeak.web.utils.Mappings._
import play.api.data.Forms._
import play.api.data._

object EventForms {
  val create: Form[Event.Data] = Form(mapping(
    "slug" -> eventSlug,
    "name" -> eventName,
    "start" -> localDateTime,
    "venue" -> optional(gMapPlace)
  )(Event.Data.apply)(Event.Data.unapply))
}
