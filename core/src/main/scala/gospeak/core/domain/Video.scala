package gospeak.core.domain

import java.time.Instant

import gospeak.libs.scala.domain.{Tag, Url}

import scala.concurrent.duration.FiniteDuration

final case class Video(url: Url.Video,
                       channel: Video.Ref,
                       playlist: Option[Video.Ref],
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

  final case class Ref(id: String, name: String)

  final case class Data(url: Url.Video,
                        channel: Ref,
                        playlist: Option[Ref],
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
