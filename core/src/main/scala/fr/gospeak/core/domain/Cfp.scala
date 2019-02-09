package fr.gospeak.core.domain

import fr.gospeak.core.domain.utils.Info
import fr.gospeak.libs.scalautils.domain.{DataClass, Markdown, SlugBuilder, UuidIdBuilder}

final case class Cfp(id: Cfp.Id,
                     group: Group.Id,
                     slug: Cfp.Slug,
                     name: Cfp.Name,
                     description: Markdown,
                     info: Info) {
  def data: Cfp.Data = Cfp.Data(slug, name, description)
}

object Cfp {

  final class Id private(value: String) extends DataClass(value)

  object Id extends UuidIdBuilder[Id]("Cfp.Id", new Id(_))

  final class Slug private(value: String) extends DataClass(value)

  object Slug extends SlugBuilder[Slug]("Cfp.Slug", new Slug(_))

  final case class Name(value: String) extends AnyVal

  final case class Data(slug: Cfp.Slug, name: Cfp.Name, description: Markdown)

}
