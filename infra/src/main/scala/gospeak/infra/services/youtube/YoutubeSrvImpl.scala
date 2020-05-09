package gospeak.infra.services.youtube

import java.time.Instant

import cats.effect.IO
import gospeak.core.domain.Video
import gospeak.core.domain.Video.ChannelRef
import gospeak.core.services.youtube.YoutubeSrv
import gospeak.infra.services.youtube.YoutubeSrvImpl.format
import gospeak.libs.scala.Extensions._
import gospeak.libs.scala.domain.CustomException
import gospeak.libs.youtube.YoutubeClient
import gospeak.libs.youtube.domain.{VideosListResponse, YoutubeErrors}

class YoutubeSrvImpl(client: YoutubeClient) extends YoutubeSrv {
  override def channelVideos(channel: ChannelRef)(now: Instant): IO[Either[CustomException, Seq[Video]]] =
    getVideos(channel.id, "")(now)

  override def playlistVideos(playlist: Video.PlaylistRef)(now: Instant): IO[Either[CustomException, Seq[Video]]] =
    getPlaylistVideos(playlist.id, "")(now)

  private def getVideos(channelId: String, pageToken: String)(now: Instant): IO[Either[CustomException, Seq[Video]]] = for {
    response <- client.searchVideos(channelId, pageToken).flatMap(toIO)
    all <- if (response.hasNextPage) {
      for {
        next <- getVideos(channelId, response.nextPageToken.get)(now)
        result <- videos(response.itemIds)(now)
      } yield result.flatMap(v => next.map(_ ++ v))
    } else videos(response.itemIds)(now)
  } yield all

  private def getPlaylistVideos(playlist: String, pageToken: String)(now: Instant): IO[Either[CustomException, Seq[Video]]] = for {
    response <- client.playlistItems(playlist, pageToken).flatMap(toIO)
    all <- if (response.hasNextPage) {
      for {
        next <- getPlaylistVideos(playlist, response.nextPageToken.get)(now)
        result <- videos(response.itemIds)(now)
      } yield result.flatMap(v => next.map(_ ++ v))
    } else videos(response.itemIds)(now)
  } yield all

  private def videos(videoIds: Seq[String])(now: Instant): IO[Either[CustomException, Seq[Video]]] = {
    for {
      response <- client.videos(videoIds).flatMap(toIO)
      videos <- IO.pure(toVideos(response, now))
    } yield videos
  }

  private def toVideos(result: VideosListResponse, now: Instant): Either[CustomException, Seq[Video]] =
    result.items.map(i => Video.from(i, now)).sequence

  private def toIO[A](e: Either[YoutubeErrors, A]): IO[A] = e.toIO(format _)
}

object YoutubeSrvImpl {
  private[youtube] def format(errors: YoutubeErrors): CustomException =
    CustomException(errors.errors.mkString("\n"))
}
