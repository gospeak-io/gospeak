package fr.gospeak.web.api.domain.utils

import fr.gospeak.core.domain.utils.BasicCtx
import gospeak.libs.scala.domain.GMapPlace
import play.api.libs.json.{Json, Writes}

case class ApiPlace(name: String,
                    address: String,
                    locality: Option[String],
                    country: String,
                    url: String,
                    geo: ApiGeo)

object ApiPlace {
  def from(p: GMapPlace)(implicit ctx: BasicCtx): ApiPlace =
    new ApiPlace(
      name = p.name,
      address = p.formatted,
      locality = p.locality,
      country = p.country,
      url = p.url,
      geo = ApiGeo(p.geo.lat, p.geo.lng))

  val unknown = ApiPlace("unknown", "Unknown address", None, "unknown", "https://maps.google.com/?cid=3360768160548514744", ApiGeo(48.8716827, 2.307039))

  implicit val writes: Writes[ApiPlace] = Json.writes[ApiPlace]
}
