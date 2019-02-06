package fr.gospeak.web.user.groups.settings

import fr.gospeak.core.domain.Cfp
import fr.gospeak.libs.scalautils.domain.Markdown
import fr.gospeak.web.utils.Mappings._
import play.api.data.Form
import play.api.data.Forms._

object SettingsForms {
  val cfpCreate: Form[Cfp.Data] = Form(mapping(
    "slug" -> cfpSlug,
    "name" -> cfpName,
    "description" -> markdown
  )(Cfp.Data.apply)(Cfp.Data.unapply))
}
