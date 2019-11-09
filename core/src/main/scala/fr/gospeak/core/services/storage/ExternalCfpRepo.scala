package fr.gospeak.core.services.storage

import java.time.Instant

import cats.effect.IO
import fr.gospeak.core.domain.{CommonCfp, ExternalCfp, User}
import fr.gospeak.libs.scalautils.domain.{Done, Page, Tag}

trait ExternalCfpRepo extends PublicExternalCfpRepo with SuggestExternalCfpRepo

trait PublicExternalCfpRepo {
  def create(data: ExternalCfp.Data, by: User.Id, now: Instant): IO[ExternalCfp]

  def edit(cfp: ExternalCfp.Id)(data: ExternalCfp.Data, by: User.Id, now: Instant): IO[Done]

  def listOpen(now: Instant, params: Page.Params): IO[Page[CommonCfp]]

  def find(cfp: ExternalCfp.Id): IO[Option[ExternalCfp]]
}

trait SuggestExternalCfpRepo {
  def listTags(): IO[Seq[Tag]]
}
