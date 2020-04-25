package gospeak.core.domain

import java.time.Instant

import gospeak.libs.youtube.domain.VideoItem

final case class YoutubeVideo(id: String,
                              etag: String,
                              channelId: Option[String],
                              description: Option[String],
                              likes: Option[Long],
                              dislikes: Option[Long],
                              publishedAt: Option[Instant],
                              kind: String,
                              title: Option[String],
                              tag: Seq[String])

object YoutubeVideo {

  def apply(videoItem: VideoItem): YoutubeVideo = new YoutubeVideo(
    videoItem.id,
    videoItem.etag,
    videoItem.channelId,
    videoItem.description,
    videoItem.likes,
    videoItem.dislikes,
    videoItem.publishedAt,
    videoItem.kind,
    videoItem.title,
    videoItem.tags
  )
}
