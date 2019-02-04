package fr.gospeak.core.domain

import java.time.Instant

import fr.gospeak.libs.scalautils.domain.{DataClass, Email, SlugBuilder, UuidIdBuilder}

final case class User(id: User.Id,
                      slug: User.Slug,
                      firstName: String,
                      lastName: String,
                      email: Email,
                      created: Instant,
                      updated: Instant) {
  def name: User.Name = User.Name(firstName, lastName)
}

object User {

  final class Id private(value: String) extends DataClass(value)

  object Id extends UuidIdBuilder[Id]("User.Id", new Id(_))

  final class Slug private(value: String) extends DataClass(value)

  object Slug extends SlugBuilder[Slug]("User.Slug", new Slug(_))

  final case class Name(value: String) extends AnyVal

  object Name {
    def apply(firstName: String, lastName: String): Name = new Name(s"$firstName $lastName")
  }

}
