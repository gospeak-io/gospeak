package fr.gospeak.core.services.storage

import java.time.Instant

import cats.effect.IO
import fr.gospeak.core.domain._
import fr.gospeak.core.domain.utils.OrgaReqCtx
import fr.gospeak.libs.scalautils.domain.{Done, Page}

trait SponsorRepo extends OrgaSponsorRepo with PublicSponsorRepo

trait OrgaSponsorRepo {
  def create(data: Sponsor.Data)(implicit ctx: OrgaReqCtx): IO[Sponsor]

  def edit(sponsor: Sponsor.Id, data: Sponsor.Data)(implicit ctx: OrgaReqCtx): IO[Done]

  def remove(sponsor: Sponsor.Id)(implicit ctx: OrgaReqCtx): IO[Done]

  def find(sponsor: Sponsor.Id)(implicit ctx: OrgaReqCtx): IO[Option[Sponsor]]

  def listFull(params: Page.Params)(implicit ctx: OrgaReqCtx): IO[Page[Sponsor.Full]]

  def listAll(group: Group.Id): IO[Seq[Sponsor]]

  def listAll(group: Group.Id, contact: Contact.Id): IO[Seq[Sponsor]]

  def listAllFull(group: Group.Id, partner: Partner.Id): IO[Seq[Sponsor.Full]]
}

trait PublicSponsorRepo {
  def listCurrentFull(group: Group.Id, now: Instant): IO[Seq[Sponsor.Full]]
}
