package fr.gospeak.web.pages.orga.partners

import fr.gospeak.core.domain.Partner
import fr.gospeak.web.utils.Mappings._
import play.api.data.Form
import play.api.data.Forms.{mapping, optional}

object PartnerForms {
  val create: Form[Partner.Data] = Form(mapping(
    "slug" -> partnerSlug,
    "name" -> partnerName,
    "notes" -> markdown,
    "description" -> optional(markdown),
    "logo" -> url,
    "twitter" -> optional(url)
  )(Partner.Data.apply)(Partner.Data.unapply))
}
