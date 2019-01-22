package fr.gospeak.core.domain

import cats.data.NonEmptyList
import fr.gospeak.core.domain.utils.{DataClass, Info, UuidIdBuilder}

final case class Group(id: Group.Id,
                       slug: Group.Slug,
                       name: Group.Name,
                       description: String,
                       owners: NonEmptyList[User.Id],
                       info: Info)

object Group {

  final class Id private(value: String) extends DataClass(value)

  object Id extends UuidIdBuilder[Group.Id]("Group.Id", new Group.Id(_))

  final case class Slug(value: String) extends AnyVal

  final case class Name(value: String) extends AnyVal

}
