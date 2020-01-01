package fr.gospeak.web.api.domain.utils

import play.api.libs.json.{Json, Writes}

case class ApiGeo(lat: Double,
                  lon: Double)

object ApiGeo {
  implicit val writes: Writes[ApiGeo] = Json.writes[ApiGeo]
}
