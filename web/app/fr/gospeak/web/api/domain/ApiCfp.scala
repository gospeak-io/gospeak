package fr.gospeak.web.api.domain

import java.time.LocalDateTime

import fr.gospeak.core.domain.utils.BasicCtx
import fr.gospeak.core.domain.{Cfp, Group}
import play.api.libs.json.{Json, Writes}

object ApiCfp {

  // data to display publicly
  final case class Published(slug: String,
                             name: String,
                             begin: Option[LocalDateTime],
                             close: Option[LocalDateTime],
                             description: String,
                             tags: Seq[String],
                             group: Option[ApiGroup.Embed])

  object Published {
    implicit val writes: Writes[Published] = Json.writes[Published]
  }

  def published(cfp: Cfp, group: Option[Group])(implicit ctx: BasicCtx): Published =
    new Published(
      slug = cfp.slug.value,
      name = cfp.name.value,
      begin = cfp.begin,
      close = cfp.close,
      description = cfp.description.value,
      tags = cfp.tags.map(_.value),
      group = group.map(ApiGroup.embed))

  // embedded data in other models, should be public
  final case class Embed(slug: String,
                         name: String,
                         begin: Option[LocalDateTime],
                         close: Option[LocalDateTime])

  object Embed {
    implicit val writes: Writes[Embed] = Json.writes[Embed]
  }

  def embed(cfp: Cfp)(implicit ctx: BasicCtx): Embed =
    new Embed(
      slug = cfp.slug.value,
      name = cfp.name.value,
      begin = cfp.begin,
      close = cfp.close)

}
