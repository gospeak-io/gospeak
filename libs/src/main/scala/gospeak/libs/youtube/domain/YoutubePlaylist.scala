package gospeak.libs.youtube.domain

import java.time.Instant

import com.google.api.services.youtube.{model => google}
import gospeak.libs.scala.domain.Url
import gospeak.libs.youtube.utils.YoutubeParser

import scala.collection.JavaConverters._

final case class YoutubePlaylist(id: Url.Videos.Playlist.Id,
                                 channelId: Url.Videos.Channel.Id,
                                 channelTitle: String,
                                 title: String,
                                 description: Option[String],
                                 publishedAt: Instant,
                                 lang: Option[String],
                                 tags: List[String],
                                 items: Long)

object YoutubePlaylist {
  // see https://developers.google.com/youtube/v3/docs/playlists#resource-representation
  def from(p: google.Playlist): Either[YoutubeErrors, YoutubePlaylist] = for {
    snippet <- Option(p.getSnippet).toRight(YoutubeErrors(s"Missing snippet for playlist ${p.getId}"))
    contentDetails <- Option(p.getContentDetails).toRight(YoutubeErrors(s"Missing content details for playlist ${p.getId}"))
    channelId <- Option(snippet.getChannelId).toRight(YoutubeErrors(s"Missing channelId for playlist ${p.getId}"))
    channelTitle <- Option(snippet.getChannelTitle).toRight(YoutubeErrors(s"Missing channelTitle for playlist ${p.getId}"))
    title <- Option(snippet.getTitle).toRight(YoutubeErrors(s"Missing title for playlist ${p.getId}"))
    publishedAt <- Option(snippet.getPublishedAt).map(YoutubeParser.toInstant).toRight(YoutubeErrors(s"Missing publishedAt for playlist ${p.getId}"))
    items <- Option(contentDetails.getItemCount).toRight(YoutubeErrors(s"Missing itemCount for playlist ${p.getId}"))
  } yield new YoutubePlaylist(
    id = Url.Videos.Playlist.Id(p.getId),
    channelId = Url.Videos.Channel.Id(channelId),
    channelTitle = channelTitle,
    title = title,
    description = Option(snippet.getDescription).filter(_.nonEmpty),
    publishedAt = publishedAt,
    lang = Option(snippet.getDefaultLanguage),
    tags = Option(snippet.getTags).map(_.asScala.toList).getOrElse(List()),
    items = items)
}
