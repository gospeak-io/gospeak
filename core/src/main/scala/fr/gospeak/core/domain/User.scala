package fr.gospeak.core.domain

import java.time.Instant

import fr.gospeak.core.domain.utils.{DataClass, Email, UuidIdBuilder}

case class User(id: User.Id,
                firstName: String,
                lastName: String,
                email: Email,
                created: Instant,
                updated: Instant) {
  def name: User.Name = User.Name(s"$firstName $lastName")
}

object User {

  class Id private(value: String) extends DataClass(value)

  object Id extends UuidIdBuilder[User.Id]("User.Id", new User.Id(_))

  case class Name(value: String) extends AnyVal

}
