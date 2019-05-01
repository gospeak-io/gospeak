package fr.gospeak.core.dto

import cats.data.{Validated, ValidatedNec}
import fr.gospeak.core.domain.utils.Info
import fr.gospeak.core.domain.{Partner, Venue}
import fr.gospeak.libs.scalautils.domain.{GMapPlace, Markdown}

// same as fr.gospeak.core.domain.Venue but with some entity already loaded
final case class VenueFull(id: Venue.Id,
                           partner: Partner,
                           address: GMapPlace,
                           description: Markdown,
                           roomSize: Option[Int],
                           info: Info) {
  def toVenue: Venue = Venue(id, partner.id, address, description, roomSize, info)
}

object VenueFull {
  def from(venue: Venue, partner: Partner): ValidatedNec[String, VenueFull] =
    validPartner(venue, partner).map { partner =>
      new VenueFull(
        id = venue.id,
        partner = partner,
        address = venue.address,
        description = venue.description,
        roomSize = venue.roomSize,
        info = venue.info)
    }

  private def validPartner(venue: Venue, partner: Partner): ValidatedNec[String, Partner] = {
    if (partner.id == venue.partner) {
      Validated.Valid(partner)
    } else {
      Validated.invalidNec(s"Partner id mismatch: expect ${venue.partner.value} but got ${partner.id.value} (${partner.name.value})")
    }
  }
}
