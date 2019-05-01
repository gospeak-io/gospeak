package fr.gospeak.web.pages.orga.cfps

import fr.gospeak.core.domain.Cfp
import fr.gospeak.web.utils.Mappings._
import play.api.data.Forms._
import play.api.data._

object CfpForms {
  val create: Form[Cfp.Data] = Form(mapping(
    "slug" -> cfpSlug,
    "name" -> cfpName,
    "start" -> optional(localDateTime),
    "end" -> optional(localDateTime),
    "description" -> markdown,
    "tags" -> tags
  )(Cfp.Data.apply)(Cfp.Data.unapply) verifying("Start of Cfp should be anterior to its end", fields => fields match {
    case data => validate(data).isDefined
  }))

  def validate(data: Cfp.Data): Option[Cfp.Data] = {
    (data.start, data.end) match {
      case (Some(start), Some(end)) if start.isAfter(end) => None
      case (_, _) => Some(Cfp.Data(data.slug, data.name, data.start, data.end, data.description, data.tags))
    }
  }
}