package fr.gospeak.web.api.domain

import java.time.Instant

import gospeak.core.domain.Group
import gospeak.core.domain.utils.BasicCtx
import fr.gospeak.web.api.domain.utils.ApiPlace
import play.api.libs.json.{Json, Writes}

object ApiGroup {

  // data to display publicly
  final case class Published(slug: String,
                             name: String,
                             contact: Option[String],
                             description: String,
                             location: Option[ApiPlace],
                             tags: Seq[String],
                             created: Instant)

  object Published {
    implicit val writes: Writes[Published] = Json.writes[Published]
  }

  def published(group: Group.Full)(implicit ctx: BasicCtx): Published =
    new Published(
      slug = group.slug.value,
      name = group.name.value,
      contact = group.contact.map(_.value),
      description = group.description.value,
      location = group.location.map(ApiPlace.from),
      tags = group.tags.map(_.value),
      created = group.info.createdAt)

  // embedded data in other models, should be public
  final case class Embed(slug: String,
                         name: String,
                         contact: Option[String],
                         description: String,
                         location: Option[ApiPlace],
                         tags: Seq[String])

  object Embed {
    implicit val writes: Writes[Embed] = Json.writes[Embed]
  }

  def embed(group: Group)(implicit ctx: BasicCtx): Embed =
    new Embed(
      slug = group.slug.value,
      name = group.name.value,
      contact = group.contact.map(_.value),
      description = group.description.value,
      location = group.location.map(ApiPlace.from),
      tags = group.tags.map(_.value))

}
