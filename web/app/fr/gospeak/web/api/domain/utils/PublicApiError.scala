package fr.gospeak.web.api.domain.utils

import play.api.libs.json.{Json, Writes}

case class PublicApiError(message: String)

object PublicApiError {
  implicit val writes: Writes[PublicApiError] = Json.writes[PublicApiError]
}
