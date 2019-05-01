package fr.gospeak.migration.domain

import java.time.format.DateTimeFormatter
import java.time.{Instant, LocalDateTime, ZoneOffset}

import fr.gospeak.core.domain.{Event => NewEvent}
import fr.gospeak.libs.scalautils.Extensions._
import fr.gospeak.libs.scalautils.domain.Markdown
import fr.gospeak.migration.domain.Event._
import fr.gospeak.migration.domain.utils.{GMapPlace, MeetupRef, Meta}

import scala.util.Try

case class Event(id: String, // Event.Id
                 meetupRef: Option[MeetupRef],
                 data: EventData,
                 meta: Meta) {
  lazy val toEvent: NewEvent = {
    val date = LocalDateTime.ofInstant(Instant.ofEpochMilli(data.date), ZoneOffset.UTC)
    // TODO [migration] missing fields: venue, roti, personCount & sponsor apero
    Try(NewEvent(
      id = NewEvent.Id.from(id).get,
      group = null, // should be set later
      cfp = None, // should be set later
      slug = NewEvent.Slug.from(formatter.format(date)).get,
      name = NewEvent.Name(data.title),
      start = date,
      description = data.description.map(Markdown).getOrElse(Markdown("")),
      venue = None, // TODO
      talks = Seq(), // should be set later
      tags = Seq(),
      info = meta.toInfo)).mapFailure(e => new Exception(s"toEvent error for $this", e)).get
  }
}

object Event {
  val formatter: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM")
}

case class EventData(title: String,
                     date: Long, // DateTime,
                     venue: Option[String], // Option[Partner.Id],
                     location: Option[GMapPlace],
                     apero: Option[String], // Option[Partner.Id],
                     talks: List[String], // List[Talk.Id],
                     description: Option[String],
                     roti: Option[String],
                     personCount: Option[Int])
