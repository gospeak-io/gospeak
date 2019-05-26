package fr.gospeak.core.services.storage

import java.time.Instant

import cats.effect.IO
import fr.gospeak.core.domain._
import fr.gospeak.libs.scalautils.domain.{Done, Page, Tag}

trait CfpRepo extends OrgaCfpRepo with SpeakerCfpRepo with UserCfpRepo with AuthCfpRepo with PublicCfpRepo with SuggestCfpRepo

trait OrgaCfpRepo {
  def create(group: Group.Id, data: Cfp.Data, by: User.Id, now: Instant): IO[Cfp]

  def edit(group: Group.Id, cfp: Cfp.Slug)(data: Cfp.Data, by: User.Id, now: Instant): IO[Done]

  def list(group: Group.Id, params: Page.Params): IO[Page[Cfp]]

  def list(ids: Seq[Cfp.Id]): IO[Seq[Cfp]]

  def list(group: Group.Id): IO[Seq[Cfp]]

  def find(group: Group.Id, slug: Cfp.Slug): IO[Option[Cfp]]

  def find(id: Event.Id): IO[Option[Cfp]]
}

trait SpeakerCfpRepo {
  def availableFor(talk: Talk.Id, params: Page.Params): IO[Page[Cfp]]

  def find(id: Cfp.Id): IO[Option[Cfp]]

  def find(slug: Cfp.Slug): IO[Option[Cfp]]
}

trait UserCfpRepo

trait AuthCfpRepo

trait PublicCfpRepo {
  val fields: CfpFields.type = CfpFields

  def listOpen(now: Instant, params: Page.Params): IO[Page[Cfp]]

  def listAllOpen(group: Group.Id, now: Instant): IO[Seq[Cfp]]

  def find(cfp: Cfp.Slug): IO[Option[Cfp]]

  def findOpen(cfp: Cfp.Slug, now: Instant): IO[Option[Cfp]]
}

trait SuggestCfpRepo {
  def list(group: Group.Id): IO[Seq[Cfp]]

  def listTags(): IO[Seq[Tag]]
}

object CfpFields {
  val name = "name"
  val close = "close"
}
