package fr.gospeak.core.services.storage

import java.time.Instant

import cats.effect.IO
import fr.gospeak.core.domain.{Group, Partner, Sponsor, User}
import fr.gospeak.libs.scalautils.domain.{Done, Page}

trait SponsorRepo extends OrgaSponsorRepo

trait OrgaSponsorRepo {
  def create(group: Group.Id, data: Sponsor.Data, by: User.Id, now: Instant): IO[Sponsor]

  def edit(group: Group.Id, sponsor: Sponsor.Id)(data: Sponsor.Data, by: User.Id, now: Instant): IO[Done]

  def remove(group: Group.Id, sponsor: Sponsor.Id): IO[Done]

  def find(group: Group.Id, sponsor: Sponsor.Id): IO[Option[Sponsor]]

  def list(group: Group.Id, params: Page.Params): IO[Page[Sponsor]]

  def listAll(group: Group.Id, partner: Partner.Id): IO[Seq[Sponsor]]
}