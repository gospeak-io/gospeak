package gospeak.web.api.domain

import gospeak.core.domain.Partner
import gospeak.core.domain.utils.{BasicCtx, Constants}
import gospeak.web.api.domain.utils.ApiSocial
import play.api.libs.json.{Json, Writes}

object ApiPartner {

  // embedded data in other models, should be public
  final case class Embed(slug: String,
                         name: String,
                         logo: String,
                         description: Option[String],
                         social: ApiSocial)

  object Embed {
    implicit val writes: Writes[Embed] = Json.writes[Embed]
  }

  def embed(p: Partner)(implicit ctx: BasicCtx): Embed =
    new Embed(
      slug = p.slug.value,
      name = p.name.value,
      logo = p.logo.value,
      description = p.description.map(_.value),
      social = ApiSocial.from(p.social))

  val unknown = new Embed(
    slug = "unknown",
    name = "Unknown",
    logo = Constants.Placeholders.unknownPartner,
    description = None,
    social = ApiSocial(None, None, None, None, None, None, None, None, None, None))

}
