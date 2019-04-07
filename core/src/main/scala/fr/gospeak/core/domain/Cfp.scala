package fr.gospeak.core.domain

import java.time.LocalDateTime

import fr.gospeak.core.domain.utils.Info
import fr.gospeak.libs.scalautils.domain._

final case class Cfp(id: Cfp.Id,
                     group: Group.Id,
                     slug: Cfp.Slug,
                     name: Cfp.Name,
                     start: Option[LocalDateTime],
                     end: Option[LocalDateTime],
                     description: Markdown,
                     info: Info) {
  def data: Cfp.Data = Cfp.Data(this)

  def isActive(now: LocalDateTime): Boolean = start.forall(_.isBefore(now)) && end.forall(_.isAfter(now))
}

object Cfp {
  def apply(group: Group.Id, data: Data, info: Info): Cfp =
    new Cfp(Id.generate(), group, data.slug, data.name, data.start, data.end, data.description, info)

  final class Id private(value: String) extends DataClass(value) with IId

  object Id extends UuidIdBuilder[Id]("Cfp.Id", new Id(_))

  final class Slug private(value: String) extends DataClass(value) with ISlug

  object Slug extends SlugBuilder[Slug]("Cfp.Slug", new Slug(_))

  final case class Name(value: String) extends AnyVal

  final case class Data(slug: Cfp.Slug, name: Cfp.Name, start: Option[LocalDateTime], end: Option[LocalDateTime], description: Markdown)

  object Data {
    def apply(cfp: Cfp): Data = new Data(cfp.slug, cfp.name, cfp.start, cfp.end, cfp.description)
  }

}
