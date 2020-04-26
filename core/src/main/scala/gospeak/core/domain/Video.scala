package gospeak.core.domain

import java.time.{Duration, Instant}

import gospeak.core.domain.Video.{ChannelRef, PlaylistRef}
import gospeak.libs.scala.domain.{CustomException, Tag, Url}
import gospeak.libs.youtube.domain.VideoItem

import scala.concurrent.duration.{FiniteDuration, NANOSECONDS}
import scala.util.Try

final case class Video(url: Url.Video,
                       channel: ChannelRef,
                       playlist: Option[PlaylistRef],
                       title: String,
                       description: String,
                       tags: Seq[Tag],
                       publishedAt: Instant,
                       duration: FiniteDuration,
                       lang: String,
                       views: Long,
                       likes: Long,
                       dislikes: Long,
                       comments: Long,
                       updatedAt: Instant) {
  def data: Video.Data = Video.Data(this)

}

object Video {

  def apply(d: Data, now: Instant): Video =
    new Video(d.url, d.channel, d.playlist, d.title, d.description, d.tags, d.publishedAt, d.duration, d.lang, d.views, d.likes, d.dislikes, d.comments, now)

  def from(videoItem: VideoItem, now: Instant): Either[CustomException, Video] =
    for {
      url <- Url.Video.from(videoItem.url)
      channelId <- videoItem.channelId.toRight(CustomException("Missing channel Id."))
      channelTitle <- videoItem.channelId.toRight(CustomException("Missing channel name."))
      publishedAt <- videoItem.publishedAt.toRight(CustomException("Missing publication date."))
      duration <- videoItem.duration.flatMap(toFiniteDuration).toRight(CustomException("Missing duration."))
      title <- videoItem.title.toRight(CustomException("Missing title."))
      description <- videoItem.title.toRight(CustomException("Missing description."))
    } yield
      new Video(
        url = url,
        channel = ChannelRef(channelId, channelTitle),
        playlist = None,
        title = title,
        description = description,
        tags = videoItem.tags.map(Tag(_)),
        publishedAt = publishedAt,
        duration = duration,
        lang = videoItem.lang.getOrElse("en"),
        views = videoItem.views.getOrElse(0),
        likes = videoItem.likes.getOrElse(0),
        dislikes = videoItem.dislikes.getOrElse(0),
        comments = videoItem.comments.getOrElse(0),
        updatedAt = now
      )

  private def toFiniteDuration(duration: String): Option[FiniteDuration] =
    Try(Duration.parse(duration)).map(v => FiniteDuration(v.toNanos, NANOSECONDS)).toOption

  final case class ChannelRef(id: String, name: String)

  final case class PlaylistRef(id: String, name: String)

  final case class Data(url: Url.Video,
                        channel: ChannelRef,
                        playlist: Option[PlaylistRef],
                        title: String,
                        description: String,
                        tags: Seq[Tag],
                        publishedAt: Instant,
                        duration: FiniteDuration,
                        lang: String,
                        views: Long,
                        likes: Long,
                        dislikes: Long,
                        comments: Long)

  object Data {
    def apply(v: Video): Data =
      new Data(v.url, v.channel, v.playlist, v.title, v.description, v.tags, v.publishedAt, v.duration, v.lang, v.views, v.likes, v.dislikes, v.comments)
  }

}
