package fr.gospeak.core.domain

import cats.data.NonEmptyList
import fr.gospeak.core.domain.utils._

import scala.concurrent.duration.FiniteDuration

final case class Talk(id: Talk.Id,
                      slug: Talk.Slug,
                      title: Talk.Title,
                      duration: FiniteDuration,
                      status: Talk.Status,
                      description: String,
                      speakers: NonEmptyList[User.Id],
                      info: Info)

object Talk {

  final class Id private(value: String) extends DataClass(value)

  object Id extends UuidIdBuilder[Id]("Talk.Id", new Id(_))

  final class Slug private(value: String) extends DataClass(value)

  object Slug extends SlugBuilder[Slug]("Talk.Slug", new Slug(_))

  final case class Title(value: String) extends AnyVal

  sealed trait Status extends Product with Serializable

  object Status extends EnumBuilder[Status]("Talk.Status") {

    case object Draft extends Status

    case object Private extends Status

    case object Public extends Status

    case object Archived extends Status

    val all: Seq[Status] = Seq(Draft, Private, Public, Archived)
  }

}
