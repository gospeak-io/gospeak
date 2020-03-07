package gospeak.core.domain.messages

import gospeak.core.domain.Cfp
import gospeak.libs.scala.Extensions._

final case class MsgCfp(slug: Cfp.Slug)

object MsgCfp {

  final case class Embed(slug: Cfp.Slug,
                         name: Cfp.Name,
                         active: Boolean,
                         publicLink: String,
                         orgaLink: String)

  object Embed {
    def unknown(id: Cfp.Id): Embed = Embed(
      slug = Cfp.Slug.from("Unknown").get,
      name = Cfp.Name("Unknown"),
      active = true,
      publicLink = "",
      orgaLink = "")
  }

}
