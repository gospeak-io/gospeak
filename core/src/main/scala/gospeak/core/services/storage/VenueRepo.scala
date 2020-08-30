package gospeak.core.services.storage

import cats.effect.IO
import gospeak.core.domain._
import gospeak.core.domain.utils.OrgaCtx
import gospeak.libs.scala.domain.{Done, Page}

trait VenueRepo extends OrgaVenueRepo with PublicVenueRepo with SuggestVenueRepo

trait OrgaVenueRepo {
  def duplicate(venue: Venue.Id)(implicit ctx: OrgaCtx): IO[(Partner, Venue, Option[Contact])]

  def create(partner: Partner.Id, data: Venue.Data)(implicit ctx: OrgaCtx): IO[Venue]

  def edit(venue: Venue.Id, data: Venue.Data)(implicit ctx: OrgaCtx): IO[Done]

  def remove(venue: Venue.Id)(implicit ctx: OrgaCtx): IO[Done]

  def listFull(params: Page.Params)(implicit ctx: OrgaCtx): IO[Page[Venue.Full]]

  def listAllFull()(implicit ctx: OrgaCtx): IO[List[Venue.Full]]

  def listCommon(params: Page.Params)(implicit ctx: OrgaCtx): IO[Page[Venue.Common]]

  def listPublic(params: Page.Params)(implicit ctx: OrgaCtx): IO[Page[Venue.Public]]

  def findPublic(venue: Venue.Id)(implicit ctx: OrgaCtx): IO[Option[Venue.Public]]

  def listAllFull(partner: Partner.Id): IO[List[Venue.Full]]

  def listAllFull(group: Group.Id, venues: List[Venue.Id]): IO[List[Venue.Full]]

  def findFull(venue: Venue.Id)(implicit ctx: OrgaCtx): IO[Option[Venue.Full]]

  def listAll(contact: Contact.Id)(implicit ctx: OrgaCtx): IO[List[Venue]]
}

trait PublicVenueRepo {
  def listFull(group: Group.Id, venues: List[Venue.Id]): IO[List[Venue.Full]]
}

trait SuggestVenueRepo {
  def listFull(group: Group.Id): IO[List[Venue.Full]]
}
