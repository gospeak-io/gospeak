package gospeak.core.services.storage

import cats.effect.IO
import gospeak.core.domain.{ExternalEvent, Video}
import gospeak.core.domain.utils.{AdminCtx, UserAwareCtx}
import gospeak.libs.scala.domain.{Done, Page, Url}

trait VideoRepo extends PublicVideoRepo with AdminVideoRepo

trait PublicVideoRepo {
  def list(params: Page.Params)(implicit ctx: UserAwareCtx): IO[Page[Video]]

  def find(video: Url.Video.Id): IO[Option[Video]]
}

trait AdminVideoRepo {
  def create(video: Video.Data, event: ExternalEvent.Id)(implicit ctx: AdminCtx): IO[Done]

  def edit(video: Video.Data, event: ExternalEvent.Id)(implicit ctx: AdminCtx): IO[Done]

  // true if deleted, false otherwise (other links referencing the video)
  def remove(video: Video.Data, event: ExternalEvent.Id)(implicit ctx: AdminCtx): IO[Boolean]

  def findRandom(): IO[Option[Video]]

  def listAll(event: ExternalEvent.Id): IO[List[Video]]

  def listAllForChannel(channelId: Url.Videos.Channel.Id): IO[List[Video]]

  def listAllForPlaylist(playlistId: Url.Videos.Playlist.Id): IO[List[Video]]

  def count(event: ExternalEvent.Id): IO[Long]

  def countForChannel(channelId: Url.Videos.Channel.Id): IO[Long]

  def countForPlaylist(playlistId: Url.Videos.Playlist.Id): IO[Long]
}
