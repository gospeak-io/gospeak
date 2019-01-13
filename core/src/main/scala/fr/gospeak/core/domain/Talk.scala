package fr.gospeak.core.domain

import fr.gospeak.core.domain.utils.{DataClass, UuidIdBuilder}

case class Talk(id: Talk.Id,
                slug: Talk.Slug,
                title: Talk.Title,
                description: String,
                speakers: Seq[User.Id]) // TODO NonEmptyList

object Talk {

  class Id private(value: String) extends DataClass(value)

  object Id extends UuidIdBuilder[Talk.Id]("Presentation.Id", new Talk.Id(_))

  case class Slug(value: String) extends AnyVal

  case class Title(value: String) extends AnyVal

}
