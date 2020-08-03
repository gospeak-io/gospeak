package gospeak.core.services.meetup.domain

import gospeak.libs.scala.domain._

final case class MeetupGroup(id: MeetupGroup.Id,
                             slug: MeetupGroup.Slug,
                             name: String,
                             logo: Option[Url],
                             description: Markdown,
                             link: Url.Meetup,
                             category: String,
                             topics: List[String],
                             address: String,
                             location: Geo)

object MeetupGroup {

  final case class Id(value: Long) extends AnyVal

  final class Slug private(value: String) extends DataClass(value) with ISlug

  object Slug {
    def from(in: String): Either[CustomException, Slug] = {
      if (in.nonEmpty) Right(new Slug(in))
      else Left(CustomException(s"'$in' is an invalid MeetupGroup.Slug"))
    }
  }

}
