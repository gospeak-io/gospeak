package fr.gospeak.web.api.domain

import fr.gospeak.core.domain.Venue
import fr.gospeak.libs.scalautils.domain.Geo
import play.api.libs.json.{Json, Writes}

case class PublicApiVenue(name: String,
                          logo: String,
                          twitter: Option[String],
                          address: String,
                          locality: Option[String],
                          country: String,
                          coords: Geo,
                          url: String)

object PublicApiVenue {
  def apply(v: Venue.Full): PublicApiVenue =
    new PublicApiVenue(
      name = v.partner.name.value,
      logo = v.partner.logo.value,
      twitter = v.partner.twitter.map(_.value),
      address = v.venue.address.formatted,
      locality = v.venue.address.locality,
      country = v.venue.address.country,
      coords = v.venue.address.geo,
      url = v.venue.address.url)

  implicit val writesGeo: Writes[Geo] = Json.writes[Geo]
  implicit val writes: Writes[PublicApiVenue] = Json.writes[PublicApiVenue]
}
