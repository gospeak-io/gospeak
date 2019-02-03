package fr.gospeak.web.user.groups

import fr.gospeak.core.domain.Group
import fr.gospeak.libs.scalautils.domain.Markdown
import fr.gospeak.web.utils.Mappings._
import play.api.data.Form
import play.api.data.Forms._

object GroupForms {

  final case class Create(slug: Group.Slug, name: Group.Name, description: Markdown)

  val create: Form[Create] = Form(mapping(
    "slug" -> groupSlug,
    "name" -> groupName,
    "description" -> markdown
  )(Create.apply)(Create.unapply))
}
