package gospeak.libs.youtube

import java.io.ByteArrayInputStream
import java.util

import cats.effect.IO
import com.google.api.client.googleapis.auth.oauth2.GoogleCredential
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport
import com.google.api.client.googleapis.json.GoogleJsonResponseException
import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.json.JsonFactory
import com.google.api.client.json.jackson2.JacksonFactory
import com.google.api.services.youtube.YouTube
import gospeak.libs.scala.domain.Secret
import gospeak.libs.youtube.YoutubeClient._
import gospeak.libs.youtube.domain._

import scala.collection.JavaConverters._
import scala.util.control.NonFatal

class YoutubeClient(val underlying: YouTube) {
  def channelBy(channelId: String): IO[Either[YoutubeErrors, ChannelsResponse]] =
    IO(underlying
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


  def playlistItems(playlistId: String, pageToken: String): IO[Either[YoutubeErrors, PlaylistItems]] = {
    IO(underlying
      .playlistItems()
      .list(contentDetails)
      .setPlaylistId(playlistId)
      .setPageToken(pageToken)
      .execute())
      .map(r => Right(PlaylistItems(r)))
      .handleErrorWith {
        case NonFatal(e: GoogleJsonResponseException) =>
          IO.pure(Left(YoutubeErrors(e)))
        case NonFatal(e) =>
          IO.raiseError(e)
      }
  }

  def playlistItems(playlistId: String): IO[Either[YoutubeErrors, PlaylistItems]] = {
    playlistItems(playlistId, "")
  }

  def searchVideos(channelId: String, pageToken: String): IO[Either[YoutubeErrors, SearchResults]] = {
    search(channelId, videoType, pageToken)
  }

  def search(channelId: String, itemType: String, pageToken: String): IO[Either[YoutubeErrors, SearchResults]] = {
    IO(underlying
      .search()
      .list(snippet)
      .setChannelId(channelId)
      .setPageToken(pageToken)
      .setMaxResults(maxResults)
      .execute())
      .map(r => Right(SearchResults(r).filter(itemType)))
      .handleErrorWith {
        case NonFatal(e: GoogleJsonResponseException) =>
          IO.pure(Left(YoutubeErrors(e)))
        case NonFatal(e) =>
          IO.raiseError(e)
      }
  }

  def videos(ids: Seq[String]): IO[Either[YoutubeErrors, VideosListResponse]] = {
    IO(underlying
      .videos()
      .list(all.mkString(separator))
      .setId(ids.mkString(separator))
      .execute())
      .map(r => Right(VideosListResponse(r)))
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
  val separator: String = ","
  val snippet: String = "snippet"
  val contentDetails: String = "contentDetails"
  val statistics: String = "statistics"
  val all: Seq[String] = Seq(snippet, contentDetails, statistics)
  val videoType: String = "youtube#video"

  def create(secret: Secret): YoutubeClient = {
    val scopes: util.List[String] = List("https://www.googleapis.com/auth/youtube.readonly").asJava
    val httpTransport: NetHttpTransport = GoogleNetHttpTransport.newTrustedTransport
    val jsonFactory: JsonFactory = JacksonFactory.getDefaultInstance
    val credential: GoogleCredential = GoogleCredential
      .fromStream(new ByteArrayInputStream(secret.decode.getBytes))
      .createScoped(scopes)
    val youtube: YouTube = new YouTube.Builder(httpTransport, jsonFactory, credential).build
    new YoutubeClient(youtube)
  }
}
