package fr.gospeak.core.services.storage

import java.time.Instant

import cats.effect.IO
import fr.gospeak.core.domain._
import fr.gospeak.core.domain.utils.OrgaCtx
import fr.gospeak.libs.scalautils.domain.{Done, Page, Tag}

trait CfpRepo extends OrgaCfpRepo with SpeakerCfpRepo with UserCfpRepo with AuthCfpRepo with PublicCfpRepo with SuggestCfpRepo

trait OrgaCfpRepo {
  def create(data: Cfp.Data)(implicit ctx: OrgaCtx): IO[Cfp]

  def edit(cfp: Cfp.Slug, data: Cfp.Data)(implicit ctx: OrgaCtx): IO[Done]

  def list(params: Page.Params)(implicit ctx: OrgaCtx): IO[Page[Cfp]]

  def list(ids: Seq[Cfp.Id]): IO[Seq[Cfp]]

  def list(group: Group.Id): IO[Seq[Cfp]]

  def find(slug: Cfp.Slug)(implicit ctx: OrgaCtx): IO[Option[Cfp]]

  def find(id: Event.Id): IO[Option[Cfp]]
}

trait SpeakerCfpRepo {
  def availableFor(talk: Talk.Id, params: Page.Params): IO[Page[Cfp]]

  def find(id: Cfp.Id): IO[Option[Cfp]]

  def findRead(slug: Cfp.Slug): IO[Option[Cfp]]
}

trait UserCfpRepo

trait AuthCfpRepo

trait PublicCfpRepo {
  def listOpen(now: Instant, params: Page.Params): IO[Page[Cfp]]

  def listAllOpen(group: Group.Id, now: Instant): IO[Seq[Cfp]]

  def find(id: Cfp.Id): IO[Option[Cfp]]

  def findRead(cfp: Cfp.Slug): IO[Option[Cfp]]

  def findOpen(cfp: Cfp.Slug, now: Instant): IO[Option[Cfp]]
}

trait SuggestCfpRepo {
  def list(group: Group.Id): IO[Seq[Cfp]]

  def listTags(): IO[Seq[Tag]]
}
