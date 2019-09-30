package fr.gospeak.core.domain

import fr.gospeak.core.domain.utils.Info
import fr.gospeak.libs.scalautils.domain._

final case class Partner(id: Partner.Id,
                         group: Group.Id,
                         slug: Partner.Slug,
                         name: Partner.Name,
                         notes: Markdown, // private infos for the group
                         description: Option[Markdown], // public description
                         logo: Url,
                         twitter: Option[Url],
                         info: Info) {
  def data: Partner.Data = Partner.Data(this)

  def users: Seq[User.Id] = info.users
}

object Partner {
  def apply(group: Group.Id, data: Data, info: Info): Partner =
    new Partner(Id.generate(), group, data.slug, data.name, data.notes, data.description, data.logo, data.twitter, info)

  final class Id private(value: String) extends DataClass(value) with IId

  object Id extends UuidIdBuilder[Id]("Partner.Id", new Id(_)) {
    val empty = new Id("00000000-0000-0000-0000-000000000000")
  }

  final class Slug private(value: String) extends DataClass(value) with ISlug

  object Slug extends SlugBuilder[Slug]("Partner.Slug", new Slug(_))

  final case class Name(value: String) extends AnyVal

  final case class Data(slug: Partner.Slug,
                        name: Partner.Name,
                        notes: Markdown,
                        description: Option[Markdown],
                        logo: Url,
                        twitter: Option[Url])

  object Data {
    def apply(p: Partner): Data = new Data(p.slug, p.name, p.notes, p.description, p.logo, p.twitter)
  }

}
