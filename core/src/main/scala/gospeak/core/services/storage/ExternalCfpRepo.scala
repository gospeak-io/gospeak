package gospeak.core.services.storage

import cats.effect.IO
import gospeak.core.domain.utils.{UserAwareCtx, UserCtx}
import gospeak.core.domain.{Cfp, CommonCfp, ExternalCfp, ExternalEvent}
import gospeak.libs.scala.domain.{Done, Page}

trait ExternalCfpRepo extends PublicExternalCfpRepo

trait PublicExternalCfpRepo {
  def create(event: ExternalEvent.Id, data: ExternalCfp.Data)(implicit ctx: UserCtx): IO[ExternalCfp]

  def edit(id: ExternalCfp.Id)(data: ExternalCfp.Data)(implicit ctx: UserCtx): IO[Done]

  def listAllIds(): IO[List[ExternalCfp.Id]]

  def listAll(event: ExternalEvent.Id): IO[List[ExternalCfp]]

  def listIncoming(params: Page.Params)(implicit ctx: UserAwareCtx): IO[Page[CommonCfp]]

  def listDuplicatesFull(p: ExternalCfp.DuplicateParams): IO[List[ExternalCfp.Full]]

  def findFull(id: ExternalCfp.Id): IO[Option[ExternalCfp.Full]]

  def findCommon(slug: Cfp.Slug): IO[Option[CommonCfp]]

  def findCommon(id: ExternalCfp.Id): IO[Option[CommonCfp]]
}
