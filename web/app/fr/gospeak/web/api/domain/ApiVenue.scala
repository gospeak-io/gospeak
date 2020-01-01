package fr.gospeak.web.api.domain

import fr.gospeak.core.domain.Venue
import fr.gospeak.core.domain.utils.BasicCtx
import fr.gospeak.web.api.domain.utils.ApiPlace
import play.api.libs.json.{Json, Writes}

object ApiVenue {

  // embedded data in other models, should be public
  final case class Embed(address: ApiPlace,
                         partner: ApiPartner.Embed)

  object Embed {
    implicit val writes: Writes[Embed] = Json.writes[Embed]
  }

  def embed(id: Venue.Id, venues: Seq[Venue.Full])(implicit ctx: BasicCtx): Embed =
    venues.find(_.id == id).map(embed).getOrElse(unknown(id))

  def embed(v: Venue.Full)(implicit ctx: BasicCtx): Embed =
    new Embed(
      address = ApiPlace.from(v.address),
      partner = ApiPartner.embed(v.partner))

  def unknown(id: Venue.Id)(implicit ctx: BasicCtx): Embed =
    new Embed(
      address = ApiPlace.unknown,
      partner = ApiPartner.unknown)

}
