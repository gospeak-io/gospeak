package fr.gospeak.web.pages.orga

import fr.gospeak.core.domain.Group
import fr.gospeak.web.utils.Mappings._
import play.api.data.Form
import play.api.data.Forms._

object GroupForms {
  val create: Form[Group.Data] = Form(mapping(
    "slug" -> groupSlug,
    "name" -> groupName,
    "description" -> markdown
  )(Group.Data.apply)(Group.Data.unapply))
}
