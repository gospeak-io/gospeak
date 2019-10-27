package fr.gospeak.web.pages.orga.sponsors

import fr.gospeak.core.domain.{Sponsor, SponsorPack}
import fr.gospeak.web.utils.Mappings._
import play.api.data.Form
import play.api.data.Forms._

object SponsorForms {
  val createPack: Form[SponsorPack.Data] = Form(mapping(
    "slug" -> sponsorPackSlug,
    "name" -> sponsorPackName,
    "description" -> markdown,
    "price" -> price,
    "duration" -> period
  )(SponsorPack.Data.apply)(SponsorPack.Data.unapply))

  val create: Form[Sponsor.Data] = Form(mapping(
    "partner" -> partnerId,
    "pack" -> sponsorPackId,
    "contact" -> optional(contactId),
    "start" -> localDate(localDateFormat),
    "finish" -> localDate(localDateFormat),
    "paid" -> optional(localDate(localDateFormat)),
    "price" -> price
  )(Sponsor.Data.apply)(Sponsor.Data.unapply))
}
