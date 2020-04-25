package gospeak.infra.services.youtube

import java.time.Instant

import cats.effect.IO
import gospeak.core.domain.Video
import gospeak.core.services.youtube.YoutubeSrv
import gospeak.infra.services.youtube.YoutubeSrvImpl.format
import gospeak.libs.scala.domain.CustomException
import gospeak.libs.youtube.YoutubeClient
import gospeak.libs.youtube.domain.{SearchResults, VideosListResponse, YoutubeErrors}
import gospeak.libs.scala.Extensions._

class YoutubeSrvImpl(youtubeClient: YoutubeClient) extends YoutubeSrv {

  override def videos(channelId: String)(now: Instant): IO[Either[CustomException, Seq[Video]]] =
    videos(channelId, "")(now)

  private def videos(channelId: String, pageToken: String)(now: Instant): IO[Either[CustomException, Seq[Video]]] = for {
    response <- youtubeClient.search(channelId, YoutubeClient.videoType, pageToken).flatMap(toIO)
    all <- if (response.hasNextPage) {
      for {
        next <- videos(channelId, response.nextPageToken.get)(now)
        result <- videos(response.itemIds)(now)
      } yield result.flatMap(v => next.map(_ ++ v))
    } else videos(response.itemIds)(now)
  } yield all

  private def videos(videoIds: Seq[String])(now: Instant): IO[Either[CustomException, Seq[Video]]] = {
    for {
      response <- youtubeClient.videos(videoIds).flatMap(toIO)
      videos <- IO.pure(toVideos(response, now))
    } yield videos
  }

  private def toIO[A](e: Either[YoutubeErrors, A]): IO[A] =
    e.toIO(e => CustomException(format(e)))

  private def toVideos(result: VideosListResponse, now: Instant): Either[CustomException, Seq[Video]] =
    result.items.map(i => Video.from(i, now)).sequence
}

object YoutubeSrvImpl {
  private[youtube] def format(errors: YoutubeErrors): String =
    errors.errors.mkString("\n")
}

