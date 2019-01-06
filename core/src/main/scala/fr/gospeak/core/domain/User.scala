package fr.gospeak.core.domain

case class User(id: User.Id,
                firstName: String)

object User {

  class Id private(val value: String) extends DataClass(value)

  object Id extends UuidIdBuilder[User.Id]("User.Id", new User.Id(_))

}
