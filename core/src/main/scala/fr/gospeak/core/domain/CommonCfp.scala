package fr.gospeak.core.domain

import java.time.LocalDateTime

import fr.gospeak.libs.scalautils.Extensions._
import fr.gospeak.libs.scalautils.domain.{GMapPlace, Logo, Markdown, Tag}

// to hold Cfp or ExternalCfp in order to list them together
final case class CommonCfp(id: String,
                           slug: Option[Cfp.Slug],
                           name: String,
                           logo: Option[Logo],
                           begin: Option[LocalDateTime],
                           close: Option[LocalDateTime],
                           location: Option[GMapPlace],
                           description: Markdown,
                           tags: Seq[Tag]) {
  def ref: Either[ExternalCfp.Id, Cfp.Slug] = slug.toEither(ExternalCfp.Id.from(id).get)
}

object CommonCfp {
  def apply(group: Group, cfp: Cfp): CommonCfp = new CommonCfp(
    id = cfp.id.value,
    slug = Some(cfp.slug),
    name = cfp.name.value,
    logo = None,
    begin = cfp.begin,
    close = cfp.close,
    location = group.location,
    description = cfp.description,
    tags = cfp.tags)

  def apply(cfp: ExternalCfp): CommonCfp = new CommonCfp(
    id = cfp.id.value,
    slug = None,
    name = cfp.name.value,
    logo = cfp.logo,
    begin = cfp.begin,
    close = cfp.close,
    location = cfp.event.location,
    description = cfp.description,
    tags = cfp.tags)
}
