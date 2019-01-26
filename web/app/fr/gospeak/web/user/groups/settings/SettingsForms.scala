package fr.gospeak.web.user.groups.settings

import fr.gospeak.core.domain.Cfp
import fr.gospeak.web.utils.Mappings._
import play.api.data.Form
import play.api.data.Forms._

object SettingsForms {

  final case class CfpCreate(slug: Cfp.Slug, name: Cfp.Name, description: String)

  val cfpCreate: Form[CfpCreate] = Form(mapping(
    "slug" -> cfpSlug,
    "name" -> cfpName,
    "description" -> nonEmptyText
  )(CfpCreate.apply)(CfpCreate.unapply))
}
