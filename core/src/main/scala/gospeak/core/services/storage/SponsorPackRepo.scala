package gospeak.core.services.storage

import cats.effect.IO
import gospeak.core.domain.utils.OrgaCtx
import gospeak.core.domain.{Group, SponsorPack}

trait SponsorPackRepo extends OrgaSponsorPackRepo with PublicSponsorPackRepo with SuggestSponsorPackRepo

trait OrgaSponsorPackRepo {
  def create(data: SponsorPack.Data)(implicit ctx: OrgaCtx): IO[SponsorPack]

  def edit(pack: SponsorPack.Slug, data: SponsorPack.Data)(implicit ctx: OrgaCtx): IO[Unit]

  def disable(pack: SponsorPack.Slug)(implicit ctx: OrgaCtx): IO[Unit]

  def enable(pack: SponsorPack.Slug)(implicit ctx: OrgaCtx): IO[Unit]

  def find(pack: SponsorPack.Slug)(implicit ctx: OrgaCtx): IO[Option[SponsorPack]]

  def listAll(group: Group.Id): IO[List[SponsorPack]]

  def listAll(implicit ctx: OrgaCtx): IO[List[SponsorPack]]

  def listActives(implicit ctx: OrgaCtx): IO[List[SponsorPack]]
}

trait PublicSponsorPackRepo {
  def listActives(group: Group.Id): IO[List[SponsorPack]]
}

trait SuggestSponsorPackRepo {
  def listAll(group: Group.Id): IO[List[SponsorPack]]
}
