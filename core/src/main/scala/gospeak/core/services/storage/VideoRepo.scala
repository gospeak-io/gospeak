package gospeak.core.services.storage

import java.time.Instant

import cats.effect.IO
import gospeak.core.domain.Video
import gospeak.core.domain.utils.UserAwareCtx
import gospeak.libs.scala.domain.{Done, Page}

trait VideoRepo extends BatchVideoRepo with PublicVideoRepo

trait BatchVideoRepo {
  def create(video: Video.Data, now: Instant): IO[Video]

  def edit(video: Video.Data, now: Instant): IO[Done]
}

trait PublicVideoRepo {
  def find(videoId: String): IO[Option[Video]]

  def list(params: Page.Params)(implicit ctx: UserAwareCtx): IO[Page[Video]]
}
