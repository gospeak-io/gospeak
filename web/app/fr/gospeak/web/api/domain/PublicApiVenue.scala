package fr.gospeak.web.api.domain

import fr.gospeak.core.domain.Venue
import fr.gospeak.libs.scalautils.domain.Geo
import fr.gospeak.web.api.domain.utils.Place
import play.api.libs.json.{Json, Writes}

object PublicApiVenue {

  case class Embedded(name: String,
                      logo: String,
                      twitter: Option[String],
                      description: Option[String],
                      address: Place)

  object Embedded {
    def apply(v: Venue.Full): Embedded =
      new Embedded(
        name = v.partner.name.value,
        logo = v.partner.logo.value,
        twitter = v.partner.twitter.map(_.value),
        description = v.partner.description.map(_.value),
        address = Place(v.address))

    implicit val geoWrites: Writes[Geo] = Json.writes[Geo]
    implicit val embeddedWrites: Writes[Embedded] = Json.writes[Embedded]
  }

}
