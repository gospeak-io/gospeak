package fr.gospeak.core.services.storage

import cats.effect.IO
import fr.gospeak.core.domain._
import fr.gospeak.core.domain.utils.OrgaCtx
import fr.gospeak.libs.scalautils.domain.{Done, Page}

trait VenueRepo extends OrgaVenueRepo with PublicVenueRepo with SuggestVenueRepo

trait OrgaVenueRepo {
  def create(data: Venue.Data)(implicit ctx: OrgaCtx): IO[Venue]

  def edit(venue: Venue.Id, data: Venue.Data)(implicit ctx: OrgaCtx): IO[Done]

  def remove(venue: Venue.Id)(implicit ctx: OrgaCtx): IO[Done]

  def listFull(params: Page.Params)(implicit ctx: OrgaCtx): IO[Page[Venue.Full]]

  def listAllFull()(implicit ctx: OrgaCtx): IO[Page[Venue.Full]]

  def listPublicFull(params: Page.Params)(implicit ctx: OrgaCtx): IO[Page[Venue.Full]]

  def listAllFull(partner: Partner.Id): IO[Seq[Venue.Full]]

  def listAllFull(venues: Seq[Venue.Id])(implicit ctx: OrgaCtx): IO[Seq[Venue.Full]]

  def findFull(venue: Venue.Id)(implicit ctx: OrgaCtx): IO[Option[Venue.Full]]

  def listAll(contact: Contact.Id)(implicit ctx: OrgaCtx): IO[Seq[Venue]]
}

trait PublicVenueRepo {
  def listFull(group: Group.Id, venues: Seq[Venue.Id]): IO[Seq[Venue.Full]]
}

trait SuggestVenueRepo {
  def listFull(group: Group.Id): IO[Seq[Venue.Full]]
}
