package fr.gospeak.core.domain

import fr.gospeak.core.domain.utils.{DataClass, UuidIdBuilder}

case class Group(id: Group.Id,
                 slug: Group.Slug,
                 name: Group.Name,
                 description: String,
                 owners: Seq[User.Id])

object Group {

  class Id private(val value: String) extends DataClass(value)

  object Id extends UuidIdBuilder[Group.Id]("Group.Id", new Group.Id(_))

  case class Slug(value: String) extends DataClass(value)

  case class Name(value: String) extends DataClass(value)

}
