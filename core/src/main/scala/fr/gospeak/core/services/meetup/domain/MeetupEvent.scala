package fr.gospeak.core.services.meetup.domain

import fr.gospeak.libs.scalautils.domain.CustomException

import scala.util.Try

object MeetupEvent {

  final case class Id(value: Long) extends AnyVal

  object Id {
    def from(in: String): Either[CustomException, Id] =
      Try(in.toLong).map(new Id(_)).toEither
        .left.map(e => CustomException(s"'$in' is an invalid MeetupEvent.Id: ${e.getMessage}"))
  }

  final case class Ref(group: MeetupGroup.Slug, event: Id) {
    def link: String = s"https://www.meetup.com/${group.value}/events/${event.value}"
  }

}
