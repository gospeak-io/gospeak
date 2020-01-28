package fr.gospeak.core.domain

import fr.gospeak.core.domain.utils.Info
import gospeak.libs.scala.domain._

case class SponsorPack(id: SponsorPack.Id,
                       group: Group.Id,
                       slug: SponsorPack.Slug,
                       name: SponsorPack.Name,
                       description: Markdown,
                       price: Price,
                       duration: TimePeriod,
                       active: Boolean,
                       info: Info) {
  def data: SponsorPack.Data = SponsorPack.Data(this)
}

object SponsorPack {
  def apply(group: Group.Id, data: Data, info: Info): SponsorPack =
    new SponsorPack(Id.generate(), group, data.slug, data.name, data.description, data.price, data.duration, active = true, info)

  final class Id private(value: String) extends DataClass(value) with IId

  object Id extends UuidIdBuilder[Id]("SponsorPack.Id", new Id(_))

  final class Slug private(value: String) extends DataClass(value) with ISlug

  object Slug extends SlugBuilder[Slug]("SponsorPack.Slug", new Slug(_))

  final case class Name(value: String) extends AnyVal

  final case class Data(slug: Slug,
                        name: Name,
                        description: Markdown,
                        price: Price,
                        duration: TimePeriod)

  object Data {
    def apply(sp: SponsorPack): Data = new Data(sp.slug, sp.name, sp.description, sp.price, sp.duration)
  }

}
