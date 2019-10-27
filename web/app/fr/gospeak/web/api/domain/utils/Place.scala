package fr.gospeak.web.api.domain.utils

import java.time.ZoneId

import fr.gospeak.libs.scalautils.domain.GMapPlace
import play.api.libs.json.{Json, Writes}

case class Place(name: String,
                 address: String,
                 locality: Option[String],
                 country: String,
                 url: String,
                 geo: Geo,
                 timezone: ZoneId)

object Place {
  def apply(p: GMapPlace): Place =
    new Place(
      name = p.name,
      address = p.formatted,
      locality = p.locality,
      country = p.country,
      url = p.url,
      geo = Geo(p.geo.lng, p.geo.lng),
      timezone = p.timezone)

  implicit val writes: Writes[Place] = Json.writes[Place]
}
