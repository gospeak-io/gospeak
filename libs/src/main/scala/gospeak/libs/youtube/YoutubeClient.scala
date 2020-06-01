package gospeak.libs.youtube

import java.io.ByteArrayInputStream

import cats.effect.IO
import com.google.api.client.googleapis.auth.oauth2.GoogleCredential
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport
import com.google.api.client.googleapis.json.GoogleJsonResponseException
import com.google.api.client.googleapis.services.AbstractGoogleClientRequest
import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.json.JsonFactory
import com.google.api.client.json.jackson2.JacksonFactory
import com.google.api.services.youtube.{YouTube, model => google}
import gospeak.libs.scala.Extensions._
import gospeak.libs.scala.domain.{Secret, Url}
import gospeak.libs.youtube.YoutubeClient._
import gospeak.libs.youtube.domain._

import scala.collection.JavaConverters._
import scala.util.Try
import scala.util.control.NonFatal

// see https://developers.google.com/youtube/v3/getting-started
class YoutubeClient(val underlying: YouTube) {
  def getChannelId(url: Url.YouTube.Channel): IO[Either[YoutubeErrors, Url.Videos.Channel.Id]] = {
    url.fold(
      id => IO.pure(Right(id)),
      user => execute(underlying.channels().list(Settings.Channel.Part.id).setForUsername(user))
        .map(_.flatMap(r => one(r.getItems, (i: google.Channel) => Option(i.getId).map(Url.Videos.Channel.Id).toRight(YoutubeErrors(s"Missing channel id"))))),
      customUrl => getChannelIdFromCustomUrl(customUrl)
    )
  }

  def getChannel(url: Url.YouTube.Channel): IO[Either[YoutubeErrors, YoutubeChannel]] = {
    import Settings.Channel.Part._
    val parts = List(snippet, topicDetails, statistics).mkString(separator)

    def execListChannels(request: YouTube#Channels#List): IO[Either[YoutubeErrors, YoutubeChannel]] =
      execute(request).map(_.flatMap(r => one(r.getItems, YoutubeChannel.from)))

    def getChannelFromId(channelId: Url.Videos.Channel.Id): IO[Either[YoutubeErrors, YoutubeChannel]] =
      execListChannels(underlying.channels().list(parts).setId(channelId.value))

    def getChannelFromUser(username: String): IO[Either[YoutubeErrors, YoutubeChannel]] =
      execListChannels(underlying.channels().list(parts).setForUsername(username))

    def getChannelFromCustomUrl(customUrl: String): IO[Either[YoutubeErrors, YoutubeChannel]] =
      getChannelIdFromCustomUrl(customUrl).flatMap(_.map(getChannelFromId).sequence).map(_.flatMap(identity))

    url.fold(
      id => getChannelFromId(id),
      user => getChannelFromUser(user),
      customUrl => getChannelFromCustomUrl(customUrl)
    )
  }

  def getPlaylist(url: Url.YouTube.Playlist): IO[Either[YoutubeErrors, YoutubePlaylist]] = {
    import Settings.Channel.Part._
    val parts = List(snippet, contentDetails).mkString(separator)
    execute(underlying.playlists().list(parts).setId(url.playlistId.value)).map(_.flatMap(r => one(r.getItems, YoutubePlaylist.from)))
  }

  def listChannelVideos(channelId: Url.Videos.Channel.Id, pageToken: String = ""): IO[Either[YoutubeErrors, YoutubePage[YoutubeVideo]]] = {
    execute(underlying.search()
      .list(Settings.Channel.Part.snippet)
      .setChannelId(channelId.value)
      .setType(Settings.Type.video)
      .setOrder("date")
      .setPageToken(pageToken)
      .setMaxResults(maxResults))
      .map(_.flatMap(YoutubeVideo.page))
  }

  def listPlaylistVideos(playlistId: Url.Videos.Playlist.Id, pageToken: String = ""): IO[Either[YoutubeErrors, YoutubePage[YoutubeVideo]]] = {
    import Settings.Channel.Part._
    execute(underlying.playlistItems()
      .list(Seq(snippet, contentDetails).mkString(separator))
      .setPlaylistId(playlistId.value)
      .setPageToken(pageToken)
      .setMaxResults(maxResults))
      .map(_.flatMap(YoutubeVideo.page))
  }

  def getVideoDetails(videoIds: Seq[Url.Video.Id]): IO[Either[YoutubeErrors, List[YoutubeVideo]]] = {
    import Settings.Channel.Part._
    execute(underlying.videos()
      .list(Seq(snippet, contentDetails, statistics).mkString(separator))
      .setId(videoIds.map(_.value).mkString(separator)))
      .map(_.flatMap(_.getItems.asScala.toList.map(YoutubeVideo.from).sequence))
  }

  // no way to get channel id from custom channel url :(
  // see https://stackoverflow.com/questions/37267324/how-to-get-youtube-channel-details-using-youtube-data-api-if-channel-has-custom
  private def getChannelIdFromCustomUrl(customUrl: String): IO[Either[YoutubeErrors, Url.Videos.Channel.Id]] =
    execute(underlying.search().list(Settings.Channel.Part.id).setType(Settings.Type.channel).setQ(customUrl))
      .map(_.flatMap(r => one(r.getItems, (i: google.SearchResult) => Option(i.getId).flatMap(id => Option(id.getChannelId)).map(Url.Videos.Channel.Id).toRight(YoutubeErrors(s"Missing channel id")))))

  private def execute[A](r: AbstractGoogleClientRequest[A]): IO[Either[YoutubeErrors, A]] =
    IO(r.execute()).map(Right(_)).handleErrorWith {
      case NonFatal(e: GoogleJsonResponseException) => IO.pure(Left(YoutubeErrors(e)))
      case NonFatal(e) => IO.raiseError(e)
    }

  private def one[A, B](list: java.util.List[A], f: A => Either[YoutubeErrors, B]): Either[YoutubeErrors, B] =
    if (list.size() == 1) f(list.get(0)) else Left(YoutubeErrors(s"Expect 1 result but got ${list.size()}"))
}

object YoutubeClient {
  private val maxResults: Long = 50L
  private val separator: String = ","

  def create(secret: Secret, appName: String): Try[YoutubeClient] = {
    Try(GoogleCredential.fromStream(new ByteArrayInputStream(secret.decode.getBytes)))
      .map(_.createScoped(List("https://www.googleapis.com/auth/youtube.readonly").asJava))
      .map { credential =>
        val httpTransport: NetHttpTransport = GoogleNetHttpTransport.newTrustedTransport
        val jsonFactory: JsonFactory = JacksonFactory.getDefaultInstance
        val youtube: YouTube = new YouTube.Builder(httpTransport, jsonFactory, credential).setApplicationName(appName).build
        new YoutubeClient(youtube)
      }
  }

  object Settings {

    object Type {
      val channel = "channel"
      val playlist = "playlist"
      val video = "video"
    }

    object Channel {

      object Part { // cf https://developers.google.com/youtube/v3/docs/channels/list#parameters
        val id = "id" // ex: "UCVelKVoLQIhwx9C2LWf-CDA"
        val contentDetails = "contentDetails" // ex: {"relatedPlaylists":{"favorites":"","likes":"","uploads":"UUVelKVoLQIhwx9C2LWf-CDA","watchHistory":"HL","watchLater":"WL"}}
        val contentOwnerDetails = "contentOwnerDetails" // ex: ???
        val snippet = "snippet" // ex: {"title":"BreizhCamp","customUrl":"breizhcamp","description":"...","publishedAt":"2013-06-17T08:51:45.000Z","thumbnails":{"default":{"width":88,"height":88,"url":"..."},"high":{"width":800,"height":800,"url":"..."},"medium":{"width":240,"height":240,"url":"..."}}}
        val statistics = "statistics" // ex: {"commentCount":"0","hiddenSubscriberCount":false,"subscriberCount":"2570","videoCount":"228","viewCount":"170131"}
        val status = "status" // ex: {"isLinked":true,"longUploadsStatus":"longUploadsUnspecified","madeForKids":false,"privacyStatus":"public"}
        val topicDetails = "topicDetails" // ex: {"topicCategories":["https://en.wikipedia.org/wiki/Technology"],"topicIds":["/m/01k8wb"]}
        val brandingSettings = "brandingSettings" // ex: {"channel":{"title":"BreizhCamp","description":"...","profileColor":"#000000","defaultTab":"Featured","showBrowseView":true,"showRelatedChannels":true},"image":{"bannerImageUrl":"...","bannerMobileImageUrl":"...","bannerTabletImageUrl":"...","bannerTvImageUrl":"..."},"hints":[{"property":"...","value":"..."}]}
        val auditDetails = "auditDetails" // needs more than readonly permissions
        val localizations = "localizations"
      }

    }

  }

}
