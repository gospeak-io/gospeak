package fr.gospeak.web.api.domain

import java.time.Instant

import fr.gospeak.core.domain.Group
import play.api.libs.json.{Json, Writes}

case class PublicApiGroup(slug: String,
                          name: String,
                          contact: Option[String],
                          description: String,
                          tags: Seq[String],
                          created: Instant)

object PublicApiGroup {
  def apply(group: Group): PublicApiGroup =
    new PublicApiGroup(
      slug = group.slug.value,
      name = group.name.value,
      contact = group.contact.map(_.value),
      description = group.description.value,
      tags = group.tags.map(_.value),
      created = group.info.created)

  case class Embedded(slug: String,
                      name: String,
                      contact: Option[String],
                      description: String,
                      tags: Seq[String])

  object Embedded {
    def apply(group: Group): Embedded =
      new Embedded(
        slug = group.slug.value,
        name = group.name.value,
        contact = group.contact.map(_.value),
        description = group.description.value,
        tags = group.tags.map(_.value))

    implicit val embeddedWrites: Writes[Embedded] = Json.writes[Embedded]
  }

  implicit val writes: Writes[PublicApiGroup] = Json.writes[PublicApiGroup]
}
