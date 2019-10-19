package fr.gospeak.core.domain

import java.time.{Instant, LocalDateTime}

import fr.gospeak.core.domain.utils.{Info, TemplateData}
import fr.gospeak.core.services.meetup.domain.MeetupEvent
import fr.gospeak.libs.scalautils.Extensions._
import fr.gospeak.libs.scalautils.domain.MustacheTmpl.MustacheMarkdownTmpl
import fr.gospeak.libs.scalautils.domain._

final case class Event(id: Event.Id,
                       group: Group.Id,
                       cfp: Option[Cfp.Id],
                       slug: Event.Slug,
                       name: Event.Name,
                       start: LocalDateTime,
                       maxAttendee: Option[Int],
                       // duration: Option[Duration]
                       description: MustacheMarkdownTmpl[TemplateData.EventInfo],
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
}

object Event {
  def create(group: Group.Id, data: Data, info: Info): Event =
    new Event(Id.generate(), group, data.cfp, data.slug, data.name, data.start, data.maxAttendee, data.description, data.venue, Seq(), data.tags, None, ExtRefs(), info)

  final class Id private(value: String) extends DataClass(value) with IId

  object Id extends UuidIdBuilder[Id]("Event.Id", new Id(_))

  final class Slug private(value: String) extends DataClass(value) with ISlug

  object Slug extends SlugBuilder[Slug]("Event.Slug", new Slug(_))

  final case class Name(value: String) extends AnyVal

  final case class ExtRefs(meetup: Option[MeetupEvent.Ref] = None)

  final case class Rsvp(event: Event.Id,
                        answer: Rsvp.Answer,
                        answeredAt: Instant,
                        user: User)

  object Rsvp {

    sealed trait Answer

    object Answer {

      case object Yes extends Answer

      case object No extends Answer

      case object Wait extends Answer

      val all: Seq[Answer] = Seq(Yes, No, Wait)

      def from(str: String): Either[CustomException, Answer] =
        all.find(_.toString == str).map(Right(_)).getOrElse(Left(CustomException(s"'$str' is not a valid Event.Rsvp.Answer")))
    }

  }

  final case class Full(event: Event, venue: Option[Venue.Full]) {
    def slug: Slug = event.slug

    def name: Name = event.name

    def start: LocalDateTime = event.start

    def talks: Seq[Proposal.Id] = event.talks

    def refs: ExtRefs = event.refs
  }

  final case class Data(cfp: Option[Cfp.Id],
                        slug: Slug,
                        name: Name,
                        start: LocalDateTime,
                        maxAttendee: Option[Int],
                        venue: Option[Venue.Id],
                        description: MustacheMarkdownTmpl[TemplateData.EventInfo],
                        tags: Seq[Tag],
                        refs: Event.ExtRefs)

  object Data {
    def apply(event: Event): Data = new Data(event.cfp, event.slug, event.name, event.start, event.maxAttendee, event.venue, event.description, event.tags, event.refs)
  }

}
