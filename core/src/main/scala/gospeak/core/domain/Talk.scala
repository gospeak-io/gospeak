package gospeak.core.domain

import cats.data.NonEmptyList
import gospeak.core.domain.utils._
import gospeak.libs.scala.domain._

import scala.concurrent.duration.FiniteDuration

final case class Talk(id: Talk.Id,
                      slug: Talk.Slug,
                      status: Talk.Status,
                      title: Talk.Title,
                      duration: FiniteDuration,
                      description: Markdown,
                      message: Markdown,
                      speakers: NonEmptyList[User.Id],
                      slides: Option[Slides],
                      video: Option[Video],
                      tags: Seq[Tag],
                      info: Info) {
  def data: Talk.Data = Talk.Data(this)

  def hasSpeaker(user: User.Id): Boolean = speakers.toList.contains(user)

  def users: Seq[User.Id] = (info.createdBy :: info.updatedBy :: speakers.toList).distinct
}

object Talk {
  def apply(d: Data,
            status: Status,
            speakers: NonEmptyList[User.Id],
            info: Info): Talk =
    new Talk(Id.generate(), d.slug, status, d.title, d.duration, d.description, d.message, speakers, d.slides, d.video, d.tags, info)

  final class Id private(value: String) extends DataClass(value) with IId

  object Id extends UuidIdBuilder[Id]("Talk.Id", new Id(_))

  final class Slug private(value: String) extends DataClass(value) with ISlug

  object Slug extends SlugBuilder[Slug]("Talk.Slug", new Slug(_))

  final case class Title(value: String) extends AnyVal

  sealed trait Status extends StringEnum {
    def value: String = toString
  }

  object Status extends EnumBuilder[Status]("Talk.Status") {

    case object Draft extends Status

    case object Private extends Status {
      def description = "Only you can see it, you can propose it to groups but organizers will not see it"
    }

    case object Listed extends Status {
      def description = "Only group organizers can see it, they will be able to ask you for a proposal for their group."
    }

    case object Public extends Status {
      def description = s"Like '$Listed', plus the talk will be in your speaker page if your profile is public "
    }

    case object Archived extends Status {
      def description = "When your talk is not actual anymore. It will be hidden everywhere"
    }

    val all: Seq[Status] = Seq(Draft, Private, Listed, Public, Archived)
    val active: NonEmptyList[Status] = NonEmptyList.of(Draft, Private, Listed, Public)
  }

  final case class Data(slug: Talk.Slug,
                        title: Talk.Title,
                        duration: FiniteDuration,
                        description: Markdown,
                        message: Markdown,
                        slides: Option[Slides],
                        video: Option[Video],
                        tags: Seq[Tag])

  object Data {
    def apply(t: Talk): Data = Data(t.slug, t.title, t.duration, t.description, t.message, t.slides, t.video, t.tags)
  }

}
