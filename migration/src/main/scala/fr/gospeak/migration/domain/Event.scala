package fr.gospeak.migration.domain

import java.time.format.DateTimeFormatter
import java.time.{Instant, LocalDateTime, ZoneOffset}

import fr.gospeak.core.domain.{Event => NewEvent, Group => NewGroup, Cfp => NewCfp, Venue => NewVenue, Proposal => NewProposal}
import fr.gospeak.libs.scalautils.Extensions._
import fr.gospeak.libs.scalautils.domain.MarkdownTemplate
import fr.gospeak.migration.domain.Event._
import fr.gospeak.migration.domain.utils.{GMapPlace, MeetupRef, Meta}

import scala.util.Try

case class Event(id: String, // Event.Id
                 meetupRef: Option[MeetupRef],
                 data: EventData,
                 meta: Meta) {
  def toEvent(group: NewGroup.Id, cfp: NewCfp.Id, venues: Seq[NewVenue], proposals: Seq[NewProposal]): NewEvent = {
    val instant = Instant.ofEpochMilli(data.date)
    val date = LocalDateTime.ofInstant(instant, ZoneOffset.UTC)
    // TODO [migration] missing fields: venue, roti, personCount & sponsor apero
    Try(NewEvent(
      id = NewEvent.Id.from(id).get,
      group = group,
      cfp = Some(cfp),
      slug = NewEvent.Slug.from(formatter.format(date)).get,
      name = NewEvent.Name(data.title),
      start = date,
      description = MarkdownTemplate.Mustache(data.description.getOrElse("")),
      venue = data.venue.map(v => venues.find(_.partner.value == v).get.id),
      talks = data.talks.map(t => proposals.find(_.talk.value == t).get.id),
      tags = Seq(),
      published = Some(instant),
      info = meta.toInfo)).mapFailure(e => new Exception(s"toEvent error for $this", e)).get
  }
}

object Event {
  val formatter: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy_MM")
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
