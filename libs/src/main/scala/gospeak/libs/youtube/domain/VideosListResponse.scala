package gospeak.libs.youtube.domain

import java.time.Instant

import com.google.api.services.youtube.model.VideoSnippet
import com.google.api.services.youtube.{model => google}

import scala.collection.JavaConverters._

final case class VideosListResponse(kind: String,
                                    pageInfo: Option[PageInfo],
                                    items: Seq[YoutubeVideo]
                                   )

case object VideosListResponse {
  def apply(videoListResponse: google.VideoListResponse): VideosListResponse =
    new VideosListResponse(videoListResponse.getKind,
      Option(videoListResponse.getPageInfo).map(PageInfo(_)),
      videoListResponse.getItems.asScala.map(YoutubeVideo(_)))
}

final case class YoutubeVideo(kind: String,
                              url: String,
                              publishedAt: Option[Instant],
                              channelId: Option[String],
                              channelTitle: Option[String],
                              title: Option[String],
                              description: Option[String],
                              likes: Option[Int],
                              dislikes: Option[Int],
                              comments: Option[Int],
                              lang: Option[String],
                              views: Option[Int],
                              duration: Option[String],
                              tags: Seq[String]
                          )

object YoutubeVideo {
  val baseUrl: String = "https://www.youtube.com/watch?v="

  def apply(video: google.Video): YoutubeVideo = {
    val maybeSnippet: Option[VideoSnippet] = Option(video.getSnippet)
    val maybeStats = Option(video.getStatistics)
    new YoutubeVideo(video.getKind,
      s"""$baseUrl${video.getId}""",
      maybeSnippet.map(d => Instant.ofEpochMilli(d.getPublishedAt.getValue)),
      maybeSnippet.map(_.getChannelId),
      maybeSnippet.map(_.getChannelTitle),
      maybeSnippet.map(_.getTitle),
      maybeSnippet.map(_.getDescription),
      maybeStats.map(_.getLikeCount.intValue()),
      maybeStats.map(_.getDislikeCount.intValue()),
      maybeStats.map(_.getCommentCount.intValue()),
      maybeSnippet.map(_.getDefaultLanguage),
      maybeStats.map(_.getViewCount.intValue()),
      Option(video.getContentDetails)
        .map(_.getDuration),
      maybeSnippet.map(s => Option(s.getTags)
        .map(_.asScala)
        .getOrElse(Seq.empty))
        .getOrElse(Seq.empty)
    )
  }
}
