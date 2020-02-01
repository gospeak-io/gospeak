package gospeak.core.services.storage

import java.time.Instant

import cats.effect.IO
import gospeak.core.domain.utils.UserCtx
import gospeak.core.domain.{Cfp, CommonCfp, ExternalCfp}
import gospeak.libs.scala.domain.{Done, Page, Tag}

trait ExternalCfpRepo extends PublicExternalCfpRepo with SuggestExternalCfpRepo

trait PublicExternalCfpRepo {
  def create(data: ExternalCfp.Data)(implicit ctx: UserCtx): IO[ExternalCfp]

  def edit(id: ExternalCfp.Id)(data: ExternalCfp.Data)(implicit ctx: UserCtx): IO[Done]

  def listIncoming(now: Instant, params: Page.Params): IO[Page[CommonCfp]]

  def listDuplicates(p: ExternalCfp.DuplicateParams): IO[Seq[ExternalCfp]]

  def find(id: ExternalCfp.Id): IO[Option[ExternalCfp]]

  def findCommon(slug: Cfp.Slug): IO[Option[CommonCfp]]

  def findCommon(id: ExternalCfp.Id): IO[Option[CommonCfp]]
}

trait SuggestExternalCfpRepo {
  def listTags(): IO[Seq[Tag]]
}
