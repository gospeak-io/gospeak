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
                      slides: Option[Url.Slides],
                      video: Option[Url.Video],
                      tags: List[Tag],
                      info: Info) {
  def data: Talk.Data = Talk.Data(this)

  def hasSpeaker(user: User.Id): Boolean = speakers.toList.contains(user)

  def speakerUsers(users: List[User]): List[User] = speakers.toList.flatMap(id => users.find(_.id == id))

  def users: List[User.Id] = (speakers.toList ++ info.users).distinct
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

    case object Public extends Status {
      def description = s"Talk on your speaker public page. Organizers can contact you to perform it."
    }

    case object Private extends Status {
      def description = "Only you can see it, you can propose it to groups but organizers can't search for it."
    }

    case object Archived extends Status {
      def description = "When your talk is not actual anymore. Will be hidden everywhere."
    }

    val all: List[Status] = List(Public, Private, Archived)
    val current: NonEmptyList[Status] = NonEmptyList.of(Public, Private)
  }

  final case class Data(slug: Talk.Slug,
                        title: Talk.Title,
                        duration: FiniteDuration,
                        description: Markdown,
                        message: Markdown,
                        slides: Option[Url.Slides],
                        video: Option[Url.Video],
                        tags: List[Tag])

  object Data {
    def apply(t: Talk): Data = Data(t.slug, t.title, t.duration, t.description, t.message, t.slides, t.video, t.tags)
  }

}
