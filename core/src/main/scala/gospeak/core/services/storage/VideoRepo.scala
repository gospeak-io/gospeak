package gospeak.core.services.storage

import cats.effect.IO
import gospeak.core.domain.Video
import gospeak.core.domain.utils.{AdminCtx, UserAwareCtx}
import gospeak.libs.scala.domain.{Done, Page}

trait VideoRepo extends PublicVideoRepo with AdminVideoRepo

trait PublicVideoRepo {
  def list(params: Page.Params)(implicit ctx: UserAwareCtx): IO[Page[Video]]

  def find(video: Video.Id): IO[Option[Video]]
}

trait AdminVideoRepo {
  def create(video: Video.Data)(implicit ctx: AdminCtx): IO[Video]

  def edit(video: Video.Data)(implicit ctx: AdminCtx): IO[Done]

  def remove(video: Video.Data)(implicit ctx: AdminCtx): IO[Done]

  def listAllForChannel(channelId: String): IO[List[Video]]

  def listAllForPlaylist(playlistId: String): IO[List[Video]]

  def countForChannel(channelId: String): IO[Long]

  def countForPlaylist(playlistId: String): IO[Long]
}
