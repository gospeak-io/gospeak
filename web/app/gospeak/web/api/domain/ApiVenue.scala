package gospeak.web.api.domain

import gospeak.core.domain.Venue
import gospeak.core.domain.utils.BasicCtx
import gospeak.web.api.domain.utils.ApiPlace
import play.api.libs.json.{Json, Writes}

object ApiVenue {

  // embedded data in other models, should be public
  final case class Embed(address: ApiPlace,
                         partner: ApiPartner.Embed)

  object Embed {
    implicit val writes: Writes[Embed] = Json.writes[Embed]
  }

  def embed(v: Venue.Full)(implicit ctx: BasicCtx): Embed =
    new Embed(
      address = ApiPlace.from(v.address),
      partner = ApiPartner.embed(v.partner))

  def unknown(id: Venue.Id)(implicit ctx: BasicCtx): Embed =
    new Embed(
      address = ApiPlace.unknown,
      partner = ApiPartner.unknown)

}
