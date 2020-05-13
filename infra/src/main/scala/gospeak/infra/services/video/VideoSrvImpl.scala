package gospeak.infra.services.video

import cats.effect.IO
import gospeak.core.domain.Video
import gospeak.core.domain.Video.PlaylistRef
import gospeak.core.services.video.{VideoSrv, YoutubeConf}
import gospeak.libs.scala.Extensions._
import gospeak.libs.scala.domain.{CustomException, Url}
import gospeak.libs.youtube.YoutubeClient
import gospeak.libs.youtube.domain.{YoutubeErrors, YoutubePlaylist}

import scala.collection.mutable
import scala.util.Try

class VideoSrvImpl(youtubeOpt: Option[YoutubeClient]) extends VideoSrv {
  private val channelIdCache = mutable.HashMap[Url.Videos.Channel, String]()

  override def getChannelId(url: Url.Videos.Channel): IO[String] = {
    channelIdCache.get(url).map(IO.pure).getOrElse {
      val res = url match {
        case u: Url.YouTube.Channel => withYoutube(_.getChannelId(u).flatMap(formatError))
        case _: Url.Vimeo.Channel => withVimeo()
      }
      res.map(id => channelIdCache.put(url, id))
      res
    }
  }

  override def listVideos(url: Url.Videos): IO[List[Video.Data]] = url match {
    case u: Url.YouTube.Channel => withYoutube(c => c.getChannelId(u).flatMap(formatError).flatMap(id => listYoutubeChannelVideos(c, id)))
    case u: Url.YouTube.Playlist => withYoutube(c => c.getPlaylist(u).flatMap(formatError).flatMap(p => listYoutubePlaylistVideos(c, p)))
    case _: Url.Vimeo.Channel => withVimeo()
    case _: Url.Vimeo.Showcase => withVimeo()
  }

  private def listYoutubeChannelVideos(client: YoutubeClient, channelId: String, pageToken: String = ""): IO[List[Video.Data]] = for {
    page <- client.listChannelVideos(channelId, pageToken).flatMap(formatError)
    videos <- getYoutubeVideoDetails(page.items.map(_.id), None)(client)
    allPages <- page.nextPageToken.map(listYoutubeChannelVideos(client, channelId, _).map(nextPage => videos ++ nextPage)).getOrElse(IO.pure(videos))
  } yield allPages

  private def listYoutubePlaylistVideos(client: YoutubeClient, playlist: YoutubePlaylist, pageToken: String = ""): IO[List[Video.Data]] = for {
    videoPage <- client.listPlaylistVideos(playlist.id, pageToken).flatMap(formatError)
    videoDetails <- getYoutubeVideoDetails(videoPage.items.map(_.id), Some(playlist))(client)
    allPages <- videoPage.nextPageToken.map(listYoutubePlaylistVideos(client, playlist, _).map(nextPage => videoDetails ++ nextPage)).getOrElse(IO.pure(videoDetails))
  } yield allPages

  private def getYoutubeVideoDetails(videoIds: Seq[String], playlist: Option[YoutubePlaylist])(client: YoutubeClient): IO[List[Video.Data]] =
    client.getVideoDetails(videoIds).flatMap(formatError).map(_.map(Video.Data.from(_, playlist.map(p => PlaylistRef(p.id, p.title)))).sequence).flatMap(_.toIO)

  private def formatError[A](errors: Either[YoutubeErrors, A]): IO[A] = errors.toIO(e => CustomException(e.errors.mkString("\n")))

  private def withYoutube[T](f: YoutubeClient => IO[T]): IO[T] = youtubeOpt.map(f).getOrElse(IO.raiseError(CustomException("YoutubeClient not available")))

  private def withVimeo[T](): IO[T] = IO.raiseError(CustomException("VimeoClient not available"))
}

object VideoSrvImpl {
  def from(youtubeConf: YoutubeConf): Try[VideoSrvImpl] = for {
    youtubeClient <- youtubeConf.secret.map(YoutubeClient.create).sequence
  } yield new VideoSrvImpl(youtubeClient)
}
