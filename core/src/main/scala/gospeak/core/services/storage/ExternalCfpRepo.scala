package gospeak.core.services.storage

import java.time.Instant

import cats.effect.IO
import gospeak.core.domain.utils.UserCtx
import gospeak.core.domain.{Cfp, CommonCfp, ExternalCfp, ExternalEvent}
import gospeak.libs.scala.domain.{Done, Page}

trait ExternalCfpRepo extends PublicExternalCfpRepo

trait PublicExternalCfpRepo {
  def create(event: ExternalEvent.Id, data: ExternalCfp.Data)(implicit ctx: UserCtx): IO[ExternalCfp]

  def edit(id: ExternalCfp.Id)(data: ExternalCfp.Data)(implicit ctx: UserCtx): IO[Done]

  def listIncoming(now: Instant, params: Page.Params): IO[Page[CommonCfp]]

  def listDuplicatesFull(p: ExternalCfp.DuplicateParams): IO[Seq[ExternalCfp.Full]]

  def findFull(id: ExternalCfp.Id): IO[Option[ExternalCfp.Full]]

  def findCommon(slug: Cfp.Slug): IO[Option[CommonCfp]]

  def findCommon(id: ExternalCfp.Id): IO[Option[CommonCfp]]
}
