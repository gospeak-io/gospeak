package gospeak.libs.youtube.domain

import java.time.Instant

import com.google.api.services.youtube.model.VideoSnippet
import com.google.api.services.youtube.{model => google}

import scala.collection.JavaConverters._

final case class VideosListResponse(kind: String,
                                    etag: String,
                                    pageInfo: PageInfo,
                                    items: Seq[VideoItem]
                                   )

case object VideosListResponse {
  def apply(videoListResponse: google.VideoListResponse): VideosListResponse =

    new VideosListResponse(videoListResponse.getKind,
      videoListResponse.getEtag,
      PageInfo(videoListResponse.getPageInfo),
      videoListResponse.getItems.asScala.map(VideoItem(_)))
}

final case class VideoItem(kind: String,
                           etag: String,
                           id: String,
                           publishedAt: Option[Instant],
                           channelId: Option[String],
                           title: Option[String],
                           description: Option[String],
                           likes: Option[Long],
                           dislikes: Option[Long],
                           tags: Seq[String]
                          )

case object VideoItem {

  def apply(video: google.Video): VideoItem = {
    val maybeSnippet: Option[VideoSnippet] = Option(video.getSnippet)
    val maybeStats = Option(video.getStatistics)
    new VideoItem(video.getKind,
      video.getEtag,
      video.getId,
      maybeSnippet.map(d => Instant.ofEpochMilli(d.getPublishedAt.getValue)),
      maybeSnippet.map(_.getChannelId),
      maybeSnippet.map(_.getTitle),
      maybeSnippet.map(_.getDescription),
      maybeStats.map(_.getLikeCount.longValue()),
      maybeStats.map(_.getDislikeCount.longValue()),
      maybeSnippet.map(s => Option(s.getTags).map(_.asScala).getOrElse(Seq.empty)).getOrElse(Seq.empty))
  }
}
