package fr.gospeak.core.services.storage

import java.time.Instant

import cats.effect.IO
import fr.gospeak.core.domain.{Group, Partner, User}
import fr.gospeak.libs.scalautils.domain.{Done, Page}

trait PartnerRepo extends OrgaPartnerRepo with SuggestPartnerRepo

trait OrgaPartnerRepo {
  def create(group: Group.Id, data: Partner.Data, by: User.Id, now: Instant): IO[Partner]

  def edit(group: Group.Id, partner: Partner.Slug)(data: Partner.Data, by: User.Id, now: Instant): IO[Done]

  def remove(group: Group.Id, partner: Partner.Slug)(by: User.Id, now: Instant): IO[Done]

  def list(group: Group.Id, params: Page.Params): IO[Page[Partner]]

  def list(partners: Seq[Partner.Id]): IO[Seq[Partner]]

  def find(group: Group.Id, slug: Partner.Slug): IO[Option[Partner]]
}

trait SuggestPartnerRepo {
  def list(group: Group.Id, params: Page.Params): IO[Page[Partner]]

  def list(group: Group.Id): IO[Seq[Partner]]
}
