package fr.gospeak.web.api.domain

import java.time.LocalDateTime

import fr.gospeak.core.domain.{Event, Proposal, User}
import play.api.libs.json.{Json, Writes}

case class EventPublicApi(slug: String,
                          name: String,
                          date: LocalDateTime,
                          // FIXME add rendered description
                          venue: Option[VenuePublicApi.Embedded],
                          talks: Seq[ProposalPublicApi.Embedded],
                          tags: Seq[String],
                          meetup: Option[String])

object EventPublicApi {
  def apply(e: Event.Full, talks: Seq[Proposal.Full], speakers: Seq[User]): EventPublicApi =
    new EventPublicApi(
      slug = e.slug.value,
      name = e.name.value,
      date = e.start,
      venue = e.venue.map(VenuePublicApi.Embedded(_)),
      talks = e.talks.flatMap(id => talks.find(_.id == id)).map(ProposalPublicApi.Embedded(_, speakers)),
      tags = e.event.tags.map(_.value),
      meetup = e.refs.meetup.map(_.link))

  case class Embedded(slug: String,
                      name: String,
                      date: LocalDateTime,
                      meetup: Option[String])

  object Embedded {
    def apply(e: Event): Embedded =
      new Embedded(
        slug = e.slug.value,
        name = e.name.value,
        date = e.start,
        meetup = e.refs.meetup.map(_.link))

    implicit val embeddedWrites: Writes[Embedded] = Json.writes[Embedded]
  }

  implicit val writes: Writes[EventPublicApi] = Json.writes[EventPublicApi]
}
