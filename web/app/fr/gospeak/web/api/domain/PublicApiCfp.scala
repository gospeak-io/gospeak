package fr.gospeak.web.api.domain

import java.time.LocalDateTime

import fr.gospeak.core.domain.{Cfp, Group}
import play.api.libs.json.{Json, Writes}

case class PublicApiCfp(slug: String,
                        name: String,
                        begin: Option[LocalDateTime],
                        close: Option[LocalDateTime],
                        description: String,
                        tags: Seq[String],
                        group: Option[PublicApiGroup.Embedded])

object PublicApiCfp {
  def apply(cfp: Cfp, group: Option[Group]): PublicApiCfp =
    new PublicApiCfp(
      slug = cfp.slug.value,
      name = cfp.name.value,
      begin = cfp.begin,
      close = cfp.close,
      description = cfp.description.value,
      tags = cfp.tags.map(_.value),
      group = group.map(PublicApiGroup.Embedded(_)))

  implicit val writes: Writes[PublicApiCfp] = Json.writes[PublicApiCfp]
}
