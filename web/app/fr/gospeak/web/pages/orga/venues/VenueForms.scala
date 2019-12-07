package fr.gospeak.web.pages.orga.venues

import fr.gospeak.core.domain.Venue
import fr.gospeak.infra.libs.timeshape.TimeShape
import fr.gospeak.web.utils.Mappings._
import play.api.data.Form
import play.api.data.Forms.{mapping, number, optional}

object VenueForms {
  def create(timeShape: TimeShape): Form[Venue.Data] = Form(mapping(
    "partner" -> partnerId,
    "contact" -> optional(contactId),
    "address" -> gMapPlace(timeShape),
    "notes" -> markdown,
    "roomSize" -> optional(number),
    "refs" -> venueRefs
  )(Venue.Data.apply)(Venue.Data.unapply))
}
