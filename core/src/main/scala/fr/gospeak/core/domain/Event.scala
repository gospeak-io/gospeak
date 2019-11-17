package fr.gospeak.core.domain

import java.time.{Instant, LocalDateTime}

import fr.gospeak.core.domain.Event.Notes
import fr.gospeak.core.domain.utils.{Constants, Info, TemplateData}
import fr.gospeak.core.services.meetup.domain.MeetupEvent
import fr.gospeak.libs.scalautils.Extensions._
import fr.gospeak.libs.scalautils.TimeUtils
import fr.gospeak.libs.scalautils.domain.MustacheTmpl.MustacheMarkdownTmpl
import fr.gospeak.libs.scalautils.domain._

final case class Event(id: Event.Id,
                       group: Group.Id,
                       cfp: Option[Cfp.Id],
                       slug: Event.Slug,
                       name: Event.Name,
                       start: LocalDateTime,
                       maxAttendee: Option[Int],
                       allowRsvp: Boolean,
                       // duration: Option[Duration]
                       description: MustacheMarkdownTmpl[TemplateData.EventInfo],
                       orgaNotes: Notes,
                       venue: Option[Venue.Id],
                       talks: Seq[Proposal.Id],
                       tags: Seq[Tag],
                       published: Option[Instant],
                       refs: Event.ExtRefs,
                       info: Info) {
  def data: Event.Data = Event.Data(this)

  def add(talk: Proposal.Id): Event = copy(talks = talks :+ talk)

  def remove(talk: Proposal.Id): Event = copy(talks = talks.filter(_ != talk))

  def move(talk: Proposal.Id, up: Boolean): Event = copy(talks = talks.swap(talk, up))

  def isPublic: Boolean = published.isDefined

  def isPast(venue: Option[Venue.Full], now: Instant): Boolean = startInstant(venue).isBefore(now)

  def canRsvp(venue: Option[Venue.Full], now: Instant): Boolean = allowRsvp && isPublic && startInstant(venue).isAfter(now)

  private def startInstant(venue: Option[Venue.Full]): Instant = TimeUtils.toInstant(start, venue.map(_.timezone).getOrElse(Constants.defaultZoneId))
}

object Event {
  def create(group: Group.Id, d: Data, info: Info): Event =
    new Event(Id.generate(), group, d.cfp, d.slug, d.name, d.start, d.maxAttendee, d.allowRsvp, d.description, Notes("", info.updated, info.updatedBy), d.venue, Seq(), d.tags, None, ExtRefs(), info)

  final class Id private(value: String) extends DataClass(value) with IId

  object Id extends UuidIdBuilder[Id]("Event.Id", new Id(_))

  final class Slug private(value: String) extends DataClass(value) with ISlug

  object Slug extends SlugBuilder[Slug]("Event.Slug", new Slug(_))

  final case class Name(value: String) extends AnyVal

  final case class Notes(text: String,
                         updatedAt: Instant,
                         updatedBy: User.Id)

  final case class ExtRefs(meetup: Option[MeetupEvent.Ref] = None)

  final case class Rsvp(event: Event.Id,
                        answer: Rsvp.Answer,
                        answeredAt: Instant,
                        user: User)

  object Rsvp {

    sealed trait Answer extends StringEnum {
      def value: String = toString
    }

    object Answer extends EnumBuilder[Answer]("Event.Rsvp.Answer") {

      case object Yes extends Answer

      case object No extends Answer

      case object Wait extends Answer

      val all: Seq[Answer] = Seq(Yes, No, Wait)

      implicit val ordering: Ordering[Answer] = new Ordering[Answer] {
        override def compare(x: Answer, y: Answer): Int = toVal(x) - toVal(y)

        private def toVal(v: Answer): Int = v match {
          case Yes => 1
          case Wait => 2
          case No => 3
        }
      }
    }

  }

  final case class Full(event: Event, venue: Option[Venue.Full], group: Group) {
    def id: Event.Id = event.id

    def slug: Slug = event.slug

    def name: Name = event.name

    def description: MustacheMarkdownTmpl[TemplateData.EventInfo] = event.description

    def start: LocalDateTime = event.start

    def talks: Seq[Proposal.Id] = event.talks

    def maxAttendee: Option[Int] = event.maxAttendee

    def allowRsvp: Boolean = event.allowRsvp

    def cfp: Option[Cfp.Id] = event.cfp

    def refs: ExtRefs = event.refs

    def isFull(yesRsvps: Long): Boolean = event.maxAttendee.exists(_ <= yesRsvps.toInt)

    def isPast(now: Instant): Boolean = event.isPast(venue, now)

    def canRsvp(now: Instant): Boolean = event.canRsvp(venue, now)
  }

  final case class Data(cfp: Option[Cfp.Id],
                        slug: Slug,
                        name: Name,
                        start: LocalDateTime,
                        maxAttendee: Option[Int],
                        allowRsvp: Boolean,
                        venue: Option[Venue.Id],
                        description: MustacheMarkdownTmpl[TemplateData.EventInfo],
                        tags: Seq[Tag],
                        refs: Event.ExtRefs)

  object Data {
    def apply(e: Event): Data = new Data(e.cfp, e.slug, e.name, e.start, e.maxAttendee, e.allowRsvp, e.venue, e.description, e.tags, e.refs)
  }

}
