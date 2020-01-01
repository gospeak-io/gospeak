package fr.gospeak.web.api.domain

import fr.gospeak.core.domain.Event
import play.api.libs.json.{Json, Writes}

case class EventOrgaApi(slug: String,
                        name: String)

object EventOrgaApi {
  def apply(e: Event): EventOrgaApi =
    EventOrgaApi(
      slug = e.slug.value,
      name = e.name.value)

  implicit val writes: Writes[EventOrgaApi] = Json.writes[EventOrgaApi]
}
