package gospeak.core.services.storage

import cats.effect.IO
import gospeak.core.domain.ExternalEvent
import gospeak.core.domain.utils.UserCtx
import gospeak.libs.scala.domain.{Done, Page, Tag}

trait ExternalEventRepo extends SpeakerExternalEventRepo with SuggestExternalEventRepo {
  def edit(id: ExternalEvent.Id)(data: ExternalEvent.Data)(implicit ctx: UserCtx): IO[Done]
}

trait SpeakerExternalEventRepo {
  def create(data: ExternalEvent.Data)(implicit ctx: UserCtx): IO[ExternalEvent]

  def list(params: Page.Params): IO[Page[ExternalEvent]]

  def find(id: ExternalEvent.Id): IO[Option[ExternalEvent]]
}

trait SuggestExternalEventRepo {
  def listTags(): IO[Seq[Tag]]
}
