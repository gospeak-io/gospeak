package gospeak.core.services.meetup.domain

import java.time.{Instant, LocalDateTime}

import gospeak.libs.scala.domain.{CustomException, Markdown}

import scala.util.Try

final case class MeetupEvent(id: MeetupEvent.Id,
                             name: String,
                             status: String,
                             visibility: String,
                             start: LocalDateTime,
                             description: Option[Markdown],
                             venue: Option[MeetupVenue],
                             rsvp_limit: Option[Int],
                             created: Instant)

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
