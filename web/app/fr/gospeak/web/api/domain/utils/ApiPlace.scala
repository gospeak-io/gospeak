package fr.gospeak.web.api.domain.utils

import fr.gospeak.core.domain.utils.BasicCtx
import fr.gospeak.libs.scalautils.domain.GMapPlace
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
      geo = ApiGeo(p.geo.lng, p.geo.lng))

  val unknown = ApiPlace("unknown", "Unknown address", None, "unknown", "https://goo.gl/maps/V9UHGdJpsSYSHqRL9", ApiGeo(48.8588376, 2.2768489))

  implicit val writes: Writes[ApiPlace] = Json.writes[ApiPlace]
}
