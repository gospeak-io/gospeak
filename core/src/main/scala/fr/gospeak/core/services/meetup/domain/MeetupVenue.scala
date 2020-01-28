package fr.gospeak.core.services.meetup.domain

import fr.gospeak.libs.scalautils.domain.{CustomException, Geo}

import scala.util.Try

final case class MeetupVenue(id: Long,
                             name: String,
                             address: String,
                             city: String,
                             country: String,
                             geo: Geo)

object MeetupVenue {

  final case class Id(value: Long) extends AnyVal

  object Id {
    def from(in: String): Either[CustomException, Id] =
      Try(in.toLong).map(new Id(_)).toEither
        .left.map(e => CustomException(s"'$in' is an invalid MeetupVenue.Id: ${e.getMessage}"))
  }

  final case class Ref(group: MeetupGroup.Slug, venue: Id)

}
