package fr.gospeak.core.domain

import cats.data.NonEmptyList
import fr.gospeak.core.domain.utils.{DataClass, Info, SlugBuilder, UuidIdBuilder}

final case class Talk(id: Talk.Id,
                      slug: Talk.Slug,
                      title: Talk.Title,
                      description: String,
                      speakers: NonEmptyList[User.Id],
                      info: Info)

object Talk {

  final class Id private(value: String) extends DataClass(value)

  object Id extends UuidIdBuilder[Id]("Talk.Id", new Id(_))

  final class Slug private(value: String) extends DataClass(value)

  object Slug extends SlugBuilder[Slug]("Talk.Slug", new Slug(_))

  final case class Title(value: String) extends AnyVal

}
