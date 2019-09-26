package fr.gospeak.web.api.domain

import java.time.LocalDateTime

import fr.gospeak.core.domain.Event
import play.api.libs.json.{Json, Writes}

case class PublicApiEvent(slug: String,
                          name: String,
                          date: LocalDateTime,
                          meetup: Option[String])

object PublicApiEvent {
  def apply(e: Event): PublicApiEvent =
    new PublicApiEvent(
      slug = e.slug.value,
      name = e.name.value,
      date = e.start,
      meetup = e.refs.meetup.map(_.link))

  implicit val writes: Writes[PublicApiEvent] = Json.writes[PublicApiEvent]
}
