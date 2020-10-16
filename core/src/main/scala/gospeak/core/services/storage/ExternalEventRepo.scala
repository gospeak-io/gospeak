package gospeak.core.services.storage

import cats.effect.IO
import gospeak.core.domain.utils.{UserAwareCtx, UserCtx}
import gospeak.core.domain.{CommonEvent, ExternalEvent}
import gospeak.libs.scala.domain.{Logo, Page, Tag}

trait ExternalEventRepo extends SpeakerExternalEventRepo with PublicExternalEventRepo with AdminExternalEventRepo with SuggestExternalEventRepo

trait PublicExternalEventRepo {
  def create(data: ExternalEvent.Data)(implicit ctx: UserCtx): IO[ExternalEvent]

  def edit(id: ExternalEvent.Id)(data: ExternalEvent.Data)(implicit ctx: UserCtx): IO[Unit]

  def listAllIds()(implicit ctx: UserAwareCtx): IO[List[ExternalEvent.Id]]

  def list(params: Page.Params)(implicit ctx: UserCtx): IO[Page[ExternalEvent]]

  def listCommon(params: Page.Params)(implicit ctx: UserAwareCtx): IO[Page[CommonEvent]]

  def find(id: ExternalEvent.Id): IO[Option[ExternalEvent]]
}

trait SpeakerExternalEventRepo {
  def create(data: ExternalEvent.Data)(implicit ctx: UserCtx): IO[ExternalEvent]

  def list(params: Page.Params)(implicit ctx: UserCtx): IO[Page[ExternalEvent]]

  def find(id: ExternalEvent.Id): IO[Option[ExternalEvent]]
}

trait AdminExternalEventRepo {
  def list(params: Page.Params)(implicit ctx: UserCtx): IO[Page[ExternalEvent]]

  def find(id: ExternalEvent.Id): IO[Option[ExternalEvent]]
}

trait SuggestExternalEventRepo {
  def listTags(): IO[List[Tag]]

  def listLogos(): IO[List[Logo]]
}
