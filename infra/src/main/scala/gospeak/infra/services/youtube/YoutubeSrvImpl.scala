package gospeak.infra.services.youtube

import cats.effect.IO
import gospeak.core.domain.YoutubeVideo
import gospeak.core.services.youtube.YoutubeSrv
import gospeak.libs.youtube.YoutubeClient

class YoutubeSrvImpl(youtubeClient: YoutubeClient) extends YoutubeSrv {

  override def videos(channelId: String): IO[Seq[YoutubeVideo]] = videos(channelId, "")

  private def videos(channelId: String, pageToken: String): IO[Seq[YoutubeVideo]] = for {
    response <- youtubeClient.search(channelId, YoutubeClient.videoType, pageToken)
    all <- response match {
      case Left(youtubeErrors) => IO.raiseError(YoutubeException(youtubeErrors.errors.mkString("\n")))
      case Right(results) =>
        if (results.hasNextPage) {
          for {
            next <- videos(channelId, results.nextPageToken.get)
            videos <- from(results.itemIds)
          } yield videos ++ next
        } else from(results.itemIds)
    }
  } yield all

  private def from(videoIds: Seq[String]): IO[Seq[YoutubeVideo]] = {
    for {
      response <- youtubeClient.videos(videoIds)
      videos <- response match {
        case Left(youtubeErrors) => IO.raiseError(YoutubeException(youtubeErrors.errors.mkString("\n")))
        case Right(results) => IO.pure(results.items.map(YoutubeVideo(_)))
      }
    } yield videos
  }
}

final case class YoutubeException(message: String) extends RuntimeException(message)

