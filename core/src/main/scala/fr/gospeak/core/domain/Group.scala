package fr.gospeak.core.domain

case class Group(id: Group.Id,
                 name: Group.Name,
                 slug: Slug,
                 owners: Seq[User.Id])

object Group {

  class Id private(val value: String) extends DataClass(value)

  object Id extends UuidIdBuilder[Group.Id]("Group.Id", new Group.Id(_))

  case class Name(value: String) extends DataClass(value)

}
