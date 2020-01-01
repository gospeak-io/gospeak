package fr.gospeak.web.api.domain

import java.time.LocalDateTime

import fr.gospeak.core.domain.{Cfp, Group}
import play.api.libs.json.{Json, Writes}

case class CfpPublicApi(slug: String,
                        name: String,
                        begin: Option[LocalDateTime],
                        close: Option[LocalDateTime],
                        description: String,
                        tags: Seq[String],
                        group: Option[GroupPublicApi.Embedded])

object CfpPublicApi {
  def apply(cfp: Cfp, group: Option[Group]): CfpPublicApi =
    new CfpPublicApi(
      slug = cfp.slug.value,
      name = cfp.name.value,
      begin = cfp.begin,
      close = cfp.close,
      description = cfp.description.value,
      tags = cfp.tags.map(_.value),
      group = group.map(GroupPublicApi.Embedded(_)))

  implicit val writes: Writes[CfpPublicApi] = Json.writes[CfpPublicApi]
}
