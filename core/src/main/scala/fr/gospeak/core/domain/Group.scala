package fr.gospeak.core.domain

import cats.data.NonEmptyList
import fr.gospeak.core.domain.utils.Info
import fr.gospeak.libs.scalautils.domain.{DataClass, Markdown, SlugBuilder, UuidIdBuilder}

final case class Group(id: Group.Id,
                       slug: Group.Slug,
                       name: Group.Name,
                       description: Markdown,
                       owners: NonEmptyList[User.Id],
                       info: Info) {
  def data: Group.Data = Group.Data(slug, name, description)
}

object Group {

  final class Id private(value: String) extends DataClass(value)

  object Id extends UuidIdBuilder[Id]("Group.Id", new Id(_))

  final class Slug private(value: String) extends DataClass(value)

  object Slug extends SlugBuilder[Slug]("Group.Slug", new Slug(_))

  final case class Name(value: String) extends AnyVal

  final case class Data(slug: Group.Slug, name: Group.Name, description: Markdown)

}
