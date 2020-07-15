package gospeak.core.services.storage

import cats.effect.IO
import gospeak.core.domain.{CommonEvent, ExternalEvent}
import gospeak.core.domain.utils.{UserAwareCtx, UserCtx}
import gospeak.libs.scala.domain.{Done, Logo, Page, Tag}

trait ExternalEventRepo extends SpeakerExternalEventRepo with PublicExternalEventRepo with AdminExternalEventRepo with SuggestExternalEventRepo

trait PublicExternalEventRepo {
  def create(data: ExternalEvent.Data)(implicit ctx: UserCtx): IO[ExternalEvent]

  def edit(id: ExternalEvent.Id)(data: ExternalEvent.Data)(implicit ctx: UserCtx): IO[Done]

  def listAllIds()(implicit ctx: UserAwareCtx): IO[Seq[ExternalEvent.Id]]

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
  def listTags(): IO[Seq[Tag]]

  def listLogos(): IO[Seq[Logo]]
}
