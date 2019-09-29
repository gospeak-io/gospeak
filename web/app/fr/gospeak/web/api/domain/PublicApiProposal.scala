package fr.gospeak.web.api.domain

import fr.gospeak.core.domain.{Proposal, User, Venue}
import play.api.libs.json.{JsNumber, Json, Writes}

import scala.concurrent.duration.FiniteDuration

case class PublicApiProposal(id: String,
                             title: String,
                             description: String,
                             duration: FiniteDuration,
                             slides: Option[String],
                             video: Option[String],
                             speakers: Seq[PublicApiSpeaker],
                             tags: Seq[String],
                             event: Option[PublicApiEvent],
                             venue: Option[PublicApiVenue])

object PublicApiProposal {
  def apply(p: Proposal.Full, speakers: Seq[User], venues: Seq[Venue.Full]): PublicApiProposal =
    new PublicApiProposal(
      id = p.id.value,
      title = p.title.value,
      description = p.description.value,
      duration = p.duration,
      slides = p.slides.map(_.value),
      video = p.video.map(_.value),
      speakers = p.speakers.toList.map(id => speakers.find(_.id == id).map(PublicApiSpeaker(_)).getOrElse(PublicApiSpeaker.unknown)),
      tags = p.tags.map(_.value),
      event = p.event.map(PublicApiEvent(_)),
      venue = p.event.flatMap(_.venue).flatMap(id => venues.find(_.id == id)).map(PublicApiVenue(_)))

  implicit val durationWrites: Writes[FiniteDuration] = (o: FiniteDuration) => JsNumber(o.toMinutes)
  implicit val writes: Writes[PublicApiProposal] = Json.writes[PublicApiProposal]
}
