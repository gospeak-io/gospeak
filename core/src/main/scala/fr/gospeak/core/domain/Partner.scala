package fr.gospeak.core.domain

import fr.gospeak.core.domain.utils.Info
import fr.gospeak.libs.scalautils.domain._

final case class Partner(id: Partner.Id,
                         group: Group.Id,
                         slug: Partner.Slug,
                         name: Partner.Name,
                         description: Markdown,
                         logo: Url,
                         info: Info) {
  def data: Partner.Data = Partner.Data(this)
}

object Partner {
  def apply(group: Group.Id, data: Data, info: Info): Partner =
    new Partner(Id.generate(), group, data.slug, data.name, data.description, data.logo, info)

  final class Id private(value: String) extends DataClass(value) with IId

  object Id extends UuidIdBuilder[Id]("Partner.Id", new Id(_))

  final class Slug private(value: String) extends DataClass(value) with ISlug

  object Slug extends SlugBuilder[Slug]("Partner.Slug", new Slug(_))

  final case class Name(value: String) extends AnyVal

  final case class Data(slug: Partner.Slug,
                        name: Partner.Name,
                        description: Markdown,
                        logo: Url)

  object Data {
    def apply(p: Partner): Data = new Data(p.slug, p.name, p.description, p.logo)
  }

}
