package fr.gospeak.web.api.domain

import fr.gospeak.core.domain.Venue
import fr.gospeak.libs.scalautils.domain.Geo
import play.api.libs.json.{Json, Writes}

object PublicApiVenue {

  case class Embedded(name: String,
                      logo: String,
                      twitter: Option[String],
                      description: Option[String],
                      address: String,
                      locality: Option[String],
                      country: String,
                      coords: Geo,
                      url: String)

  object Embedded {
    def apply(v: Venue.Full): Embedded =
      new Embedded(
        name = v.partner.name.value,
        logo = v.partner.logo.value,
        twitter = v.partner.twitter.map(_.value),
        description = v.partner.description.map(_.value),
        address = v.address.formatted,
        locality = v.address.locality,
        country = v.address.country,
        coords = v.address.geo,
        url = v.address.url)

    implicit val geoWrites: Writes[Geo] = Json.writes[Geo]
    implicit val embeddedWrites: Writes[Embedded] = Json.writes[Embedded]
  }

}
