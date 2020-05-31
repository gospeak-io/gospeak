package gospeak.core.services.storage

import cats.effect.IO
import gospeak.core.domain.Video
import gospeak.core.domain.utils.{AdminCtx, UserAwareCtx}
import gospeak.libs.scala.domain.{Done, Page, Url}

trait VideoRepo extends PublicVideoRepo with AdminVideoRepo

trait PublicVideoRepo {
  def list(params: Page.Params)(implicit ctx: UserAwareCtx): IO[Page[Video]]

  def find(video: Url.Video.Id): IO[Option[Video]]
}

trait AdminVideoRepo {
  def create(video: Video.Data)(implicit ctx: AdminCtx): IO[Video]

  def edit(video: Video.Data)(implicit ctx: AdminCtx): IO[Done]

  def remove(video: Video.Data)(implicit ctx: AdminCtx): IO[Done]

  def findRandom(): IO[Option[Video]]

  def listAllForChannel(channelId: Url.Videos.Channel.Id): IO[List[Video]]

  def listAllForPlaylist(playlistId: Url.Videos.Playlist.Id): IO[List[Video]]

  def countForChannel(channelId: Url.Videos.Channel.Id): IO[Long]

  def countForPlaylist(playlistId: Url.Videos.Playlist.Id): IO[Long]
}
