package gospeak.core.domain

import java.time.{Duration, Instant}

import gospeak.core.domain.Video.{ChannelRef, PlaylistRef}
import gospeak.libs.scala.domain.{CustomException, Tag, Url}
import gospeak.libs.youtube.domain.YoutubeVideo

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
                       views: Int,
                       likes: Int,
                       dislikes: Int,
                       comments: Int,
                       updatedAt: Instant) {
  def data: Video.Data = Video.Data(this)

}

object Video {

  def apply(d: Data, now: Instant): Video =
    new Video(d.url, d.channel, d.playlist, d.title, d.description, d.tags, d.publishedAt, d.duration, d.lang, d.views, d.likes, d.dislikes, d.comments, now)

  def from(video: YoutubeVideo, now: Instant): Either[CustomException, Video] =
    for {
      url <- Url.Video.from(video.url)
      channelId <- video.channelId.toRight(CustomException("Missing channel Id."))
      channelTitle <- video.channelTitle.toRight(CustomException("Missing channel name."))
      publishedAt <- video.publishedAt.toRight(CustomException("Missing publication date."))
      duration <- video.duration.flatMap(toFiniteDuration).toRight(CustomException("Missing duration."))
      title <- video.title.toRight(CustomException("Missing title."))
      description <- video.description.toRight(CustomException("Missing description."))
    } yield
      new Video(
        url = url,
        channel = ChannelRef(channelId, channelTitle),
        playlist = None,
        title = title,
        description = description,
        tags = video.tags.map(Tag(_)),
        publishedAt = publishedAt,
        duration = duration,
        lang = video.lang.getOrElse("en"),
        views = video.views.getOrElse(0),
        likes = video.likes.getOrElse(0),
        dislikes = video.dislikes.getOrElse(0),
        comments = video.comments.getOrElse(0),
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
                        views: Int,
                        likes: Int,
                        dislikes: Int,
                        comments: Int)

  object Data {
    def apply(v: Video): Data =
      new Data(v.url, v.channel, v.playlist, v.title, v.description, v.tags, v.publishedAt, v.duration, v.lang, v.views, v.likes, v.dislikes, v.comments)
  }

}
