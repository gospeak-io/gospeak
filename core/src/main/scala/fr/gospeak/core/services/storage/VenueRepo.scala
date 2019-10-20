package fr.gospeak.core.services.storage

import java.time.Instant

import cats.effect.IO
import fr.gospeak.core.domain.{Group, Partner, User, Venue}
import fr.gospeak.libs.scalautils.domain.{Done, Page}

trait VenueRepo extends OrgaVenueRepo with PublicVenueRepo with SuggestVenueRepo

trait OrgaVenueRepo {
  def create(group: Group.Id, data: Venue.Data, by: User.Id, now: Instant): IO[Venue]

  def edit(group: Group.Id, id: Venue.Id)(data: Venue.Data, by: User.Id, now: Instant): IO[Done]

  def listFull(group: Group.Id, params: Page.Params): IO[Page[Venue.Full]]

  def listFull(partner: Partner.Id): IO[Seq[Venue.Full]]

  def listFull(group: Group.Id, ids: Seq[Venue.Id]): IO[Seq[Venue.Full]]

  def findFull(group: Group.Id, id: Venue.Id): IO[Option[Venue.Full]]
}

trait PublicVenueRepo {
  def listFull(group: Group.Id, ids: Seq[Venue.Id]): IO[Seq[Venue.Full]]
}

trait SuggestVenueRepo {
  def listFull(group: Group.Id): IO[Seq[Venue.Full]]
}
