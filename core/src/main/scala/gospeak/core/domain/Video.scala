package gospeak.core.domain

import java.time.Instant

import gospeak.core.domain.Video.{ChannelRef, PlaylistRef}
import gospeak.core.domain.utils.Constants
import gospeak.libs.scala.domain._
import gospeak.libs.youtube.domain.YoutubeVideo

import scala.concurrent.duration.FiniteDuration

final case class Video(url: Url.Video,
                       channel: ChannelRef,
                       playlist: Option[PlaylistRef],
                       title: String,
                       description: String,
                       tags: List[Tag],
                       publishedAt: Instant,
                       duration: FiniteDuration,
                       lang: String,
                       views: Long,
                       likes: Long,
                       dislikes: Long,
                       comments: Long,
                       updatedAt: Instant) {
  val id: Url.Video.Id = url.videoId

  def data: Video.Data = Video.Data(this)

  /*
    https://vimeo.com/10516714
      https://i.vimeocdn.com/video/55277040.jpg
      https://i.vimeocdn.com/video/55277040_590x332.jpg
      https://i.vimeocdn.com/video/55277040_260x146.jpg
      https://i.vimeocdn.com/video/55277040_130x73.jpg
   */

  def thumbnail: String = url match {
    case _: Url.YouTube => s"https://i.ytimg.com/vi/${id.value}/default.jpg" // 120x90
    case _: Url.Vimeo => Constants.Placeholders.videoCover
    case _: Url.Infoq => Constants.Placeholders.videoCover
  }

  def thumbnailMedium: String = url match {
    case _: Url.YouTube => s"https://i.ytimg.com/vi/${id.value}/mqdefault.jpg" // 320x180
    case _: Url.Vimeo => Constants.Placeholders.videoCover
    case _: Url.Infoq => Constants.Placeholders.videoCover
  }

  def thumbnailHigh: String = url match {
    case _: Url.YouTube => s"https://i.ytimg.com/vi/${id.value}/hqdefault.jpg" // 480x360
    case _: Url.Vimeo => Constants.Placeholders.videoCover
    case _: Url.Infoq => Constants.Placeholders.videoCover
  }

  def channelUrl: String = url match {
    case _: Url.YouTube => s"https://www.youtube.com/channel/${channel.id.value}"
    case _: Url.Vimeo => s"https://vimeo.com/${channel.id.value}"
    case _: Url.Infoq => s"https://www.infoq.com/${channel.id.value}"
  }

  def playlistUrl: Option[(PlaylistRef, String)] = playlist.map { p =>
    url match {
      case _: Url.YouTube => p -> s"https://www.youtube.com/playlist?list=${p.id.value}"
      case _: Url.Vimeo => p -> s"https://vimeo.com/showcase/${p.id.value}"
      case _: Url.Infoq => p -> s"https://www.infoq.com/${p.id.value}"
    }
  }
}

object Video {
  def apply(d: Data, now: Instant): Video =
    new Video(d.url, d.channel, d.playlist, d.title, d.description, d.tags, d.publishedAt, d.duration, d.lang, d.views, d.likes, d.dislikes, d.comments, now)

  final case class ChannelRef(id: Url.Videos.Channel.Id, name: String)

  final case class PlaylistRef(id: Url.Videos.Playlist.Id, name: String)

  final case class Sources(videoId: Url.Video.Id,
                           talk_id: Option[Talk.Id],
                           proposal_id: Option[Proposal.Id],
                           external_proposal_id: Option[ExternalProposal.Id],
                           external_event_id: Option[ExternalEvent.Id])

  final case class Data(url: Url.Video,
                        channel: ChannelRef,
                        playlist: Option[PlaylistRef],
                        title: String,
                        description: String,
                        tags: List[Tag],
                        publishedAt: Instant,
                        duration: FiniteDuration,
                        lang: String,
                        views: Long,
                        likes: Long,
                        dislikes: Long,
                        comments: Long) {
    val id: Url.Video.Id = url.videoId
  }

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
