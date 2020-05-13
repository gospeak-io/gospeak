package gospeak.libs.youtube.domain

import java.time.Instant

import com.google.api.services.youtube.{model => google}
import gospeak.libs.scala.Extensions._
import gospeak.libs.scala.TimeUtils
import gospeak.libs.youtube.utils.YoutubeParser

import scala.collection.JavaConverters._
import scala.concurrent.duration.FiniteDuration

final case class YoutubeVideo(id: String,
                              channelId: String,
                              channelTitle: String,
                              playlistId: Option[String],
                              playlistTitle: Option[String],
                              title: String,
                              description: Option[String],
                              publishedAt: Instant,
                              lang: Option[String],
                              tags: List[String],
                              duration: Option[FiniteDuration],
                              views: Option[Long],
                              likes: Option[Long],
                              dislikes: Option[Long],
                              comments: Option[Long]) {
  def thumbnail: String = s"https://i.ytimg.com/vi/$id/default.jpg" // 120x90
  def thumbnailMedium: String = s"https://i.ytimg.com/vi/$id/mqdefault.jpg" // 320x180
  def thumbnailHigh: String = s"https://i.ytimg.com/vi/$id/hqdefault.jpg" // 480x360
  def thumbnailStandard: String = s"https://i.ytimg.com/vi/$id/sddefault.jpg" // 640x480
  def thumbnailMaxres: String = s"https://i.ytimg.com/vi/$id/maxresdefault.jpg" // 1280x720
}

object YoutubeVideo {
  // see https://developers.google.com/youtube/v3/docs/videos#resource-representation
  def from(v: google.Video): Either[YoutubeErrors, YoutubeVideo] = for {
    snippet <- Option(v.getSnippet).toRight(YoutubeErrors(s"Missing snippet for video ${v.getId}"))
    contentDetails <- Option(v.getContentDetails).toRight(YoutubeErrors(s"Missing contentDetails for video ${v.getId}"))
    statistics <- Option(v.getStatistics).toRight(YoutubeErrors(s"Missing statistics for video ${v.getId}"))
    channelId <- Option(snippet.getChannelId).toRight(YoutubeErrors(s"Missing channelId for video ${v.getId}"))
    channelTitle <- Option(snippet.getChannelTitle).toRight(YoutubeErrors(s"Missing channelTitle for video ${v.getId}"))
    title <- Option(snippet.getTitle).toRight(YoutubeErrors(s"Missing title for video ${v.getId}"))
    publishedAt <- Option(snippet.getPublishedAt).map(YoutubeParser.toInstant).toRight(YoutubeErrors(s"Missing publishedAt for video ${v.getId}"))
    duration <- Option(contentDetails.getDuration).map(d => TimeUtils.toFiniteDuration(d).toEither.left.map(e => YoutubeErrors(s"Invalid duration ($d) for video ${v.getId}: ${e.getMessage}"))).sequence
  } yield new YoutubeVideo(
    id = v.getId,
    channelId = channelId,
    channelTitle = channelTitle,
    playlistId = None,
    playlistTitle = None,
    title = title,
    description = Option(snippet.getDescription).filter(_.nonEmpty),
    publishedAt = publishedAt,
    lang = Option(snippet.getDefaultLanguage),
    tags = Option(snippet.getTags).map(_.asScala.toList).getOrElse(List()),
    duration = duration,
    views = Option(statistics.getViewCount).map(_.longValue()),
    likes = Option(statistics.getLikeCount).map(_.longValue()),
    dislikes = Option(statistics.getDislikeCount).map(_.longValue()),
    comments = Option(statistics.getCommentCount).map(_.longValue()))

  // see https://developers.google.com/youtube/v3/docs/search#resource-representation
  def from(v: google.SearchResult): Either[YoutubeErrors, YoutubeVideo] = for {
    id <- Option(v.getId).flatMap(id => Option(id.getVideoId)).toRight(YoutubeErrors(s"Missing id for video $v"))
    snippet <- Option(v.getSnippet).toRight(YoutubeErrors(s"Missing snippet for video ${v.getId}"))
    channelId <- Option(snippet.getChannelId).toRight(YoutubeErrors(s"Missing channelId for video ${v.getId}"))
    channelTitle <- Option(snippet.getChannelTitle).toRight(YoutubeErrors(s"Missing channelTitle for video ${v.getId}"))
    title <- Option(snippet.getTitle).toRight(YoutubeErrors(s"Missing title for video ${v.getId}"))
    publishedAt <- Option(snippet.getPublishedAt).map(YoutubeParser.toInstant).toRight(YoutubeErrors(s"Missing publishedAt for video ${v.getId}"))
  } yield new YoutubeVideo(
    id = id,
    channelId = channelId,
    channelTitle = channelTitle,
    playlistId = None,
    playlistTitle = None,
    title = title,
    description = Option(snippet.getDescription).filter(_.nonEmpty),
    publishedAt = publishedAt,
    lang = None,
    tags = List(),
    duration = None,
    views = None, // no statistics in SearchResult :(
    likes = None,
    dislikes = None,
    comments = None)

  // see https://developers.google.com/youtube/v3/docs/playlistItems#resource-representation
  def from(v: google.PlaylistItem): Either[YoutubeErrors, YoutubeVideo] = for {
    snippet <- Option(v.getSnippet).toRight(YoutubeErrors(s"Missing snippet for video ${v.getId}"))
    contentDetails <- Option(v.getContentDetails).toRight(YoutubeErrors(s"Missing contentDetails for video ${v.getId}"))
    id <- Option(contentDetails.getVideoId).toRight(YoutubeErrors(s"Missing id for video $v"))
    channelId <- Option(snippet.getChannelId).toRight(YoutubeErrors(s"Missing channelId for video ${v.getId}"))
    channelTitle <- Option(snippet.getChannelTitle).toRight(YoutubeErrors(s"Missing channelTitle for video ${v.getId}"))
    playlistId <- Option(snippet.getPlaylistId).toRight(YoutubeErrors(s"Missing playlistId for video ${v.getId}"))
    title <- Option(snippet.getTitle).toRight(YoutubeErrors(s"Missing title for video ${v.getId}"))
    publishedAt <- Option(contentDetails.getVideoPublishedAt).map(YoutubeParser.toInstant).toRight(YoutubeErrors(s"Missing publishedAt for video ${v.getId}"))
  } yield new YoutubeVideo(
    id = id,
    channelId = channelId,
    channelTitle = channelTitle,
    playlistId = Some(playlistId),
    playlistTitle = None,
    title = title,
    description = Option(snippet.getDescription).filter(_.nonEmpty),
    publishedAt = publishedAt,
    lang = None,
    tags = List(),
    duration = None,
    views = None, // no statistics in PlaylistItem :(
    likes = None,
    dislikes = None,
    comments = None)

  def page(r: google.SearchListResponse): Either[YoutubeErrors, YoutubePage[YoutubeVideo]] =
    r.getItems.asScala.toList.map(YoutubeVideo.from).sequence.map { items =>
      YoutubePage(
        items = items,
        nextPageToken = Option(r.getNextPageToken))
    }

  def page(r: google.PlaylistItemListResponse): Either[YoutubeErrors, YoutubePage[YoutubeVideo]] =
    r.getItems.asScala.toList.map(YoutubeVideo.from).sequence.map { items =>
      YoutubePage(
        items = items,
        nextPageToken = Option(r.getNextPageToken))
    }

}
