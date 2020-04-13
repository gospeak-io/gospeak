package gospeak.libs.youtube

import cats.effect.IO
import com.google.api.client.googleapis.json.GoogleJsonResponseException
import com.google.api.client.json.jackson2.JacksonFactory
import com.google.api.services.youtube.YouTube
import gospeak.libs.youtube.YoutubeClient._
import gospeak.libs.youtube.domain._

import scala.util.control.NonFatal

class YoutubeClient(val underlying: YouTube) {

  def channelBy(channelId: String): IO[Either[YoutubeErrors, ChannelsResponse]] =
    IO.pure(underlying
      .channels()
      .list(contentDetails)
      .setId(channelId)
      .execute())
      .map(r => Right(ChannelsResponse(r)))
      .handleErrorWith {
        case NonFatal(e: GoogleJsonResponseException) =>
          IO.pure(Left(YoutubeErrors(e)))
        case NonFatal(e) =>
          IO.raiseError(e)
      }

  def playlistItems(playlistId: String): IO[Either[YoutubeErrors, PlaylistItems]] =
    IO.pure(underlying
      .playlistItems()
      .list(contentDetails)
      .setPlaylistId(playlistId)
      .execute())
      .map(r => Right(PlaylistItems(r)))
      .handleErrorWith {
        case NonFatal(e: GoogleJsonResponseException) =>
          IO.pure(Left(YoutubeErrors(e)))
        case NonFatal(e) =>
          IO.raiseError(e)
      }


  def search(channelId: String): IO[Either[YoutubeErrors, SearchResults]] = {
    IO.pure(underlying
      .search()
      .list(snippet)
      .setChannelId(channelId)
      .setMaxResults(maxResults)
      .execute())
      .map(r => Right(SearchResults(r)))
      .handleErrorWith {
        case NonFatal(e: GoogleJsonResponseException) =>
          IO.pure(Left(YoutubeErrors(e)))
        case NonFatal(e) =>
          IO.raiseError(e)
      }
  }

}

object YoutubeClient {
  val maxResults: Long = 50L
  val snippet: String = "snippet"
  val contentDetails: String = "contentDetails"
  val JSON_FACTORY: JacksonFactory = JacksonFactory.getDefaultInstance
}

