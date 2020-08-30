package gospeak.core.services.storage

import cats.effect.IO
import gospeak.core.domain._
import gospeak.core.domain.utils.{OrgaCtx, UserAwareCtx, UserCtx}
import gospeak.libs.scala.domain.{Done, Page, Tag}

trait CfpRepo extends OrgaCfpRepo with SpeakerCfpRepo with UserCfpRepo with AuthCfpRepo with PublicCfpRepo with SuggestCfpRepo

trait OrgaCfpRepo {
  def create(data: Cfp.Data)(implicit ctx: OrgaCtx): IO[Cfp]

  def edit(cfp: Cfp.Slug, data: Cfp.Data)(implicit ctx: OrgaCtx): IO[Done]

  def list(params: Page.Params)(implicit ctx: OrgaCtx): IO[Page[Cfp]]

  def list(ids: List[Cfp.Id]): IO[List[Cfp]]

  def find(id: Cfp.Id): IO[Option[Cfp]]

  def find(slug: Cfp.Slug)(implicit ctx: OrgaCtx): IO[Option[Cfp]]

  def find(id: Event.Id): IO[Option[Cfp]]
}

trait SpeakerCfpRepo {
  def availableFor(talk: Talk.Id, params: Page.Params)(implicit ctx: UserCtx): IO[Page[Cfp]]

  def find(id: Cfp.Id): IO[Option[Cfp]]

  def findRead(slug: Cfp.Slug): IO[Option[Cfp]]
}

trait UserCfpRepo

trait AuthCfpRepo

trait PublicCfpRepo {
  def listAllPublicSlugs()(implicit ctx: UserAwareCtx): IO[List[Cfp.Slug]]

  def listAllIncoming(group: Group.Id)(implicit ctx: UserAwareCtx): IO[List[Cfp]]

  def find(id: Cfp.Id): IO[Option[Cfp]]

  def findRead(cfp: Cfp.Slug): IO[Option[Cfp]]

  def findIncoming(cfp: Cfp.Slug)(implicit ctx: UserAwareCtx): IO[Option[Cfp]]
}

trait SuggestCfpRepo {
  def list(group: Group.Id): IO[List[Cfp]]

  def listTags(): IO[List[Tag]]
}
