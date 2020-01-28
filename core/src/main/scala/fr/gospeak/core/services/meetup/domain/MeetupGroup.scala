package fr.gospeak.core.services.meetup.domain

import gospeak.libs.scala.domain.{CustomException, DataClass, ISlug, Url}

final case class MeetupGroup(id: MeetupGroup.Id,
                             slug: MeetupGroup.Slug,
                             name: String,
                             description: String,
                             photo: Url,
                             link: Url,
                             city: String,
                             country: String)

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
