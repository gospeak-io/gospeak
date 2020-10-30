package gospeak.core.services.storage

import java.time.Instant

import cats.effect.IO
import gospeak.core.domain._
import gospeak.core.domain.utils.OrgaCtx
import gospeak.libs.scala.domain.Page

trait SponsorRepo extends OrgaSponsorRepo with PublicSponsorRepo

trait OrgaSponsorRepo {
  def create(data: Sponsor.Data)(implicit ctx: OrgaCtx): IO[Sponsor]

  def edit(sponsor: Sponsor.Id, data: Sponsor.Data)(implicit ctx: OrgaCtx): IO[Unit]

  def remove(sponsor: Sponsor.Id)(implicit ctx: OrgaCtx): IO[Unit]

  def find(sponsor: Sponsor.Id)(implicit ctx: OrgaCtx): IO[Option[Sponsor]]

  def listFull(params: Page.Params)(implicit ctx: OrgaCtx): IO[Page[Sponsor.Full]]

  def listAll(implicit ctx: OrgaCtx): IO[List[Sponsor]]

  def listAll(contact: Contact.Id)(implicit ctx: OrgaCtx): IO[List[Sponsor]]

  def listAllFull(partner: Partner.Id)(implicit ctx: OrgaCtx): IO[List[Sponsor.Full]]

  def listCurrentFull(group: Group.Id, now: Instant): IO[List[Sponsor.Full]]
}

trait PublicSponsorRepo {
  def listCurrentFull(group: Group.Id, now: Instant): IO[List[Sponsor.Full]]
}
