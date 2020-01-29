package gospeak.core.services.storage

import java.time.Instant

import cats.effect.IO
import gospeak.core.domain.{Cfp, CommonCfp, ExternalCfp, User}
import gospeak.libs.scala.domain.{Done, Page, Tag}

trait ExternalCfpRepo extends PublicExternalCfpRepo with SuggestExternalCfpRepo

trait PublicExternalCfpRepo {
  def create(data: ExternalCfp.Data, by: User.Id, now: Instant): IO[ExternalCfp]

  def edit(cfp: ExternalCfp.Id)(data: ExternalCfp.Data, by: User.Id, now: Instant): IO[Done]

  def listIncoming(now: Instant, params: Page.Params): IO[Page[CommonCfp]]

  def listDuplicates(p: ExternalCfp.DuplicateParams): IO[Seq[ExternalCfp]]

  def find(cfp: ExternalCfp.Id): IO[Option[ExternalCfp]]

  def findCommon(cfp: Cfp.Slug): IO[Option[CommonCfp]]

  def findCommon(cfp: ExternalCfp.Id): IO[Option[CommonCfp]]
}

trait SuggestExternalCfpRepo {
  def listTags(): IO[Seq[Tag]]
}
