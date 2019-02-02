package fr.gospeak.core.domain

import cats.data.NonEmptyList
import fr.gospeak.core.domain.utils._
import fr.gospeak.libs.scalautils.domain.{DataClass, EnumBuilder, SlugBuilder, UuidIdBuilder}

import scala.concurrent.duration.FiniteDuration

final case class Talk(id: Talk.Id,
                      slug: Talk.Slug,
                      title: Talk.Title,
                      duration: FiniteDuration,
                      status: Talk.Status,
                      description: String,
                      speakers: NonEmptyList[User.Id],
                      info: Info) {
  def data: Talk.Data = Talk.Data(slug, title, duration, description)
}

object Talk {

  final class Id private(value: String) extends DataClass(value)

  object Id extends UuidIdBuilder[Id]("Talk.Id", new Id(_))

  final class Slug private(value: String) extends DataClass(value)

  object Slug extends SlugBuilder[Slug]("Talk.Slug", new Slug(_))

  final case class Title(value: String) extends AnyVal

  sealed trait Status extends Product with Serializable

  object Status extends EnumBuilder[Status]("Talk.Status") {

    case object Draft extends Status

    case object Private extends Status {
      def description = "Only you can see it, you can propose it to groups but organizers will not see it"
    }

    case object Public extends Status {
      def description = "Group organizers will be able to search for it and send you speaking proposals"
    }

    case object Archived extends Status {
      def description = "When your talk is not actual anymore. It will be hided by default everywhere"
    }

    val all: Seq[Status] = Seq(Draft, Private, Public, Archived)
  }

  final case class Data(slug: Talk.Slug, title: Talk.Title, duration: FiniteDuration, description: String)

}
