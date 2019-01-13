package fr.gospeak.core.domain

import fr.gospeak.core.domain.utils.{DataClass, UuidIdBuilder}

case class User(id: User.Id,
                firstName: String,
                lastName: String) {
  def name: User.Name = User.Name(s"$firstName $lastName")
}

object User {

  class Id private(value: String) extends DataClass(value)

  object Id extends UuidIdBuilder[User.Id]("User.Id", new User.Id(_))

  case class Name(value: String) extends AnyVal

}
