package fr.gospeak.migration.domain

import java.time.format.DateTimeFormatter
import java.time.{Instant, LocalDateTime, ZoneOffset}

import fr.gospeak.core.services.meetup.domain.{MeetupEvent, MeetupGroup}
import fr.gospeak.core.{domain => gs}
import fr.gospeak.libs.scalautils.Extensions._
import fr.gospeak.libs.scalautils.domain.MustacheTmpl.MustacheMarkdownTmpl
import fr.gospeak.migration.domain.Event._
import fr.gospeak.migration.domain.utils.{GMapPlace, MeetupRef, Meta}

import scala.util.Try

case class Event(id: String, // Event.Id
                 meetupRef: Option[MeetupRef],
                 data: EventData,
                 meta: Meta) {
  def toEvent(group: gs.Group.Id, cfp: gs.Cfp.Id, venues: Seq[gs.Venue], proposals: Seq[gs.Proposal]): gs.Event = {
    val instant = Instant.ofEpochMilli(data.date)
    val date = LocalDateTime.ofInstant(instant, ZoneOffset.UTC)
    Try(gs.Event(
      id = gs.Event.Id.from(id).get,
      group = group,
      cfp = Some(cfp),
      slug = gs.Event.Slug.from(formatter.format(date)).get,
      name = gs.Event.Name(data.title),
      start = date,
      description = MustacheMarkdownTmpl(data.description.getOrElse("")),
      venue = data.venue.map(v => venues.find(_.partner.value == v).getOrElse(throw new Exception(s"Missing venue $v")).id),
      talks = data.talks.map(t => proposals.find(_.talk.value == t).getOrElse(throw new Exception(s"Missing proposal $t")).id),
      tags = Seq(),
      published = Some(instant),
      refs = gs.Event.ExtRefs(
        meetup = meetupRef.map(r => MeetupEvent.Ref(MeetupGroup.Slug.from(r.group).get, MeetupEvent.Id(r.id)))),
      info = meta.toInfo)).mapFailure(e => new Exception(s"toEvent error for ${data.title}: ${e.getMessage}", e)).get
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
