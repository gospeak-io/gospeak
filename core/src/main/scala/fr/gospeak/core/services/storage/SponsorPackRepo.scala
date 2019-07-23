package fr.gospeak.core.services.storage

import java.time.Instant

import cats.effect.IO
import fr.gospeak.core.domain.{Group, SponsorPack, User}
import fr.gospeak.libs.scalautils.domain.Done

trait SponsorPackRepo extends OrgaSponsorPackRepo with SuggestSponsorPackRepo

trait OrgaSponsorPackRepo {
  def create(group: Group.Id, data: SponsorPack.Data, by: User.Id, now: Instant): IO[SponsorPack]

  def edit(group: Group.Id, pack: SponsorPack.Slug)(data: SponsorPack.Data, by: User.Id, now: Instant): IO[Done]

  def disable(group: Group.Id, pack: SponsorPack.Slug)(by: User.Id, now: Instant): IO[Done]

  def enable(group: Group.Id, pack: SponsorPack.Slug)(by: User.Id, now: Instant): IO[Done]

  def find(group: Group.Id, pack: SponsorPack.Slug): IO[Option[SponsorPack]]

  def listAll(group: Group.Id): IO[Seq[SponsorPack]]

  def listActives(group: Group.Id): IO[Seq[SponsorPack]]
}

trait SuggestSponsorPackRepo {
  def listAll(group: Group.Id): IO[Seq[SponsorPack]]
}
