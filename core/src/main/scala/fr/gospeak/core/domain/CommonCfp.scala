package fr.gospeak.core.domain

import java.time.LocalDateTime

import fr.gospeak.libs.scalautils.Extensions._
import fr.gospeak.libs.scalautils.domain.{Markdown, Tag}

// to hold Cfp or ExternalCfp in order to list them together
final case class CommonCfp(id: String,
                           slug: Option[Cfp.Slug],
                           name: String,
                           begin: Option[LocalDateTime],
                           close: Option[LocalDateTime],
                           description: Markdown,
                           tags: Seq[Tag]) {
  def ref: Either[ExternalCfp.Id, Cfp.Slug] = slug.toEither(ExternalCfp.Id.from(id).get)
}

object CommonCfp {
  def apply(cfp: Cfp): CommonCfp = new CommonCfp(
    id = cfp.id.value,
    slug = Some(cfp.slug),
    name = cfp.name.value,
    begin = cfp.begin,
    close = cfp.close,
    description = cfp.description,
    tags = cfp.tags)

  def apply(cfp: ExternalCfp): CommonCfp = new CommonCfp(
    id = cfp.id.value,
    slug = None,
    name = cfp.name.value,
    begin = cfp.begin,
    close = cfp.close,
    description = cfp.description,
    tags = cfp.tags)
}
