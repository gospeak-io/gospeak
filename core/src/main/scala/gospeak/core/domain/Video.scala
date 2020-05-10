package gospeak.core.domain

import java.time.Instant

import gospeak.core.domain.Video.{ChannelRef, PlaylistRef}
import gospeak.libs.scala.domain.{CustomException, Tag, Url}
import gospeak.libs.youtube.domain.YoutubeVideo

import scala.concurrent.duration.FiniteDuration

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

    def from(video: YoutubeVideo, playlistRef: Option[PlaylistRef]): Either[CustomException, Video.Data] = for {
      url <- Url.YouTube.Video.fromId(video.id)
      duration <- video.duration.toRight(CustomException(s"Missing duration for video $url"))
    } yield new Data(
      url = url,
      channel = ChannelRef(video.channelId, video.channelTitle),
      playlist = playlistRef,
      title = video.title,
      description = video.description.getOrElse(""),
      tags = video.tags.map(Tag(_)),
      publishedAt = video.publishedAt,
      duration = duration,
      lang = video.lang.getOrElse("en"),
      views = video.views.getOrElse(0L),
      likes = video.likes.getOrElse(0L),
      dislikes = video.dislikes.getOrElse(0L),
      comments = video.comments.getOrElse(0L))
  }

}
