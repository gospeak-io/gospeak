package fr.gospeak.core.services.storage

import cats.effect.IO
import fr.gospeak.core.domain.utils.OrgaReqCtx
import fr.gospeak.core.domain.{Group, SponsorPack}
import fr.gospeak.libs.scalautils.domain.Done

trait SponsorPackRepo extends OrgaSponsorPackRepo with PublicSponsorPackRepo with SuggestSponsorPackRepo

trait OrgaSponsorPackRepo {
  def create(data: SponsorPack.Data)(implicit ctx: OrgaReqCtx): IO[SponsorPack]

  def edit(pack: SponsorPack.Slug, data: SponsorPack.Data)(implicit ctx: OrgaReqCtx): IO[Done]

  def disable(pack: SponsorPack.Slug)(implicit ctx: OrgaReqCtx): IO[Done]

  def enable(pack: SponsorPack.Slug)(implicit ctx: OrgaReqCtx): IO[Done]

  def find(pack: SponsorPack.Slug)(implicit ctx: OrgaReqCtx): IO[Option[SponsorPack]]

  def listAll(group: Group.Id): IO[Seq[SponsorPack]]

  def listAll(implicit ctx: OrgaReqCtx): IO[Seq[SponsorPack]]

  def listActives(group: Group.Id): IO[Seq[SponsorPack]]
}

trait PublicSponsorPackRepo {
  def listActives(group: Group.Id): IO[Seq[SponsorPack]]
}

trait SuggestSponsorPackRepo {
  def listAll(group: Group.Id): IO[Seq[SponsorPack]]
}
