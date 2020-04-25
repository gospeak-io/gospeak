package gospeak.core.domain

import java.time.Instant

import gospeak.libs.scala.domain.{CustomException, Tag, Url}
import gospeak.libs.youtube.domain.VideoItem

import scala.concurrent.duration.FiniteDuration

final case class Video(url: Url.Video,
                       channel: Option[Video.Ref],
                       playlist: Option[Video.Ref],
                       title: String,
                       description: String,
                       tags: Seq[Tag],
                       publishedAt: Option[Instant],
                       duration: FiniteDuration,
                       lang: Option[String],
                       views: Long,
                       likes: Long,
                       dislikes: Long,
                       comments: Long,
                       updatedAt: Instant) {
  def data: Video.Data = Video.Data(this)

}

object Video {
  val empty: String = ""

  def apply(d: Data, now: Instant): Video =
    new Video(d.url, d.channel, d.playlist, d.title, d.description, d.tags, d.publishedAt, d.duration, d.lang, d.views, d.likes, d.dislikes, d.comments, now)

  def from(videoItem: VideoItem, now: Instant): Either[CustomException, Video] =
    for {
      url <- Url.Video.from(videoItem.url)
    } yield
      new Video(
        url = url,
        channel = videoItem.channelId.map(c => Ref(c, empty)),
        playlist = None,
        title = videoItem.title.getOrElse(empty),
        description = videoItem.description.getOrElse(empty),
        tags = videoItem.tags.map(Tag(_)),
        publishedAt = videoItem.publishedAt,
        duration = videoItem.duration.get,
        lang = videoItem.lang,
        views = videoItem.views.getOrElse(0),
        likes = videoItem.likes.getOrElse(0),
        dislikes = videoItem.dislikes.getOrElse(0),
        comments = videoItem.comments.getOrElse(0),
        updatedAt = now
      )

  final case class Ref(id: String, name: String)

  final case class Data(url: Url.Video,
                        channel: Option[Ref],
                        playlist: Option[Ref],
                        title: String,
                        description: String,
                        tags: Seq[Tag],
                        publishedAt: Option[Instant],
                        duration: FiniteDuration,
                        lang: Option[String],
                        views: Long,
                        likes: Long,
                        dislikes: Long,
                        comments: Long)

  object Data {
    def apply(v: Video): Data =
      new Data(v.url, v.channel, v.playlist, v.title, v.description, v.tags, v.publishedAt, v.duration, v.lang, v.views, v.likes, v.dislikes, v.comments)
  }

}
