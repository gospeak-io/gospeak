package gospeak.core.services.storage

import cats.effect.IO
import gospeak.core.domain.utils.OrgaCtx
import gospeak.core.domain.{Group, Partner}
import gospeak.libs.scala.domain.Page

trait PartnerRepo extends OrgaPartnerRepo with SuggestPartnerRepo

trait OrgaPartnerRepo {
  def create(data: Partner.Data)(implicit ctx: OrgaCtx): IO[Partner]

  def edit(partner: Partner.Slug, data: Partner.Data)(implicit ctx: OrgaCtx): IO[Unit]

  def remove(partner: Partner.Slug)(implicit ctx: OrgaCtx): IO[Unit]

  def list(params: Page.Params)(implicit ctx: OrgaCtx): IO[Page[Partner]]

  def listFull(params: Page.Params)(implicit ctx: OrgaCtx): IO[Page[Partner.Full]]

  def list(partners: List[Partner.Id]): IO[List[Partner]]

  def find(partner: Partner.Id)(implicit ctx: OrgaCtx): IO[Option[Partner]]

  def find(partner: Partner.Slug)(implicit ctx: OrgaCtx): IO[Option[Partner]]

  def find(group: Group.Id, partner: Partner.Slug): IO[Option[Partner]]
}

trait SuggestPartnerRepo {
  def list(params: Page.Params)(implicit ctx: OrgaCtx): IO[Page[Partner]]

  def list(group: Group.Id): IO[List[Partner]]
}
