package fr.gospeak.web.api.domain.utils

import play.api.libs.json.{Json, Writes}

case class Geo(lat: Double,
               lon: Double)

object Geo {
  implicit val writes: Writes[Geo] = Json.writes[Geo]
}
