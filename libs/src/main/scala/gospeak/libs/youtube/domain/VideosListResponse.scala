package gospeak.libs.youtube.domain

import java.time.{Duration, Instant}

import com.google.api.services.youtube.model.VideoSnippet
import com.google.api.services.youtube.{model => google}

import scala.collection.JavaConverters._
import scala.concurrent.duration.{FiniteDuration, NANOSECONDS}
import scala.util.Try

final case class VideosListResponse(kind: String,
                                    pageInfo: Option[PageInfo],
                                    items: Seq[VideoItem]
                                   )

case object VideosListResponse {
  def apply(videoListResponse: google.VideoListResponse): VideosListResponse =
    new VideosListResponse(videoListResponse.getKind,
      Option(videoListResponse.getPageInfo).map(PageInfo(_)),
      videoListResponse.getItems.asScala.map(VideoItem(_)))
}

final case class VideoItem(kind: String,
                           url: String,
                           publishedAt: Option[Instant],
                           channelId: Option[String],
                           title: Option[String],
                           description: Option[String],
                           likes: Option[Long],
                           dislikes: Option[Long],
                           comments: Option[Long],
                           lang: Option[String],
                           views: Option[Long],
                           duration: Option[FiniteDuration],
                           tags: Seq[String]
                          )

object VideoItem {
  val baseUrl: String = "https://www.youtube.com/watch?v="

  def apply(video: google.Video): VideoItem = {
    val maybeSnippet: Option[VideoSnippet] = Option(video.getSnippet)
    val maybeStats = Option(video.getStatistics)
    new VideoItem(video.getKind,
      s"""$baseUrl${video.getId}""",
      maybeSnippet.map(d => Instant.ofEpochMilli(d.getPublishedAt.getValue)),
      maybeSnippet.map(_.getChannelId),
      maybeSnippet.map(_.getTitle),
      maybeSnippet.map(_.getDescription),
      maybeStats.map(_.getLikeCount.longValue()),
      maybeStats.map(_.getDislikeCount.longValue()),
      maybeStats.map(_.getCommentCount.longValue()),
      maybeSnippet.map(_.getDefaultLanguage),
      maybeStats.map(_.getViewCount.longValue()),
      Option(video.getContentDetails)
        .map(_.getDuration)
        .flatMap(toFiniteDuration),
      maybeSnippet.map(s => Option(s.getTags)
        .map(_.asScala)
        .getOrElse(Seq.empty))
        .getOrElse(Seq.empty)
    )
  }

  def toFiniteDuration(duration: String): Option[FiniteDuration] =
    Try(Duration.parse(duration)).map(v => FiniteDuration(v.toNanos, NANOSECONDS)).toOption
}
