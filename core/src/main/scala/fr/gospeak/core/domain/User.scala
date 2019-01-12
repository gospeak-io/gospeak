package fr.gospeak.core.domain

import fr.gospeak.core.domain.utils.{DataClass, UuidIdBuilder}

case class User(id: User.Id,
                firstName: String,
                lastName: String) {
  def name: String = s"$firstName $lastName"
}

object User {

  class Id private(val value: String) extends DataClass(value)

  object Id extends UuidIdBuilder[User.Id]("User.Id", new User.Id(_))

}
