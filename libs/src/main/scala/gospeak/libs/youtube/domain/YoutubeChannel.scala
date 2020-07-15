package gospeak.libs.youtube.domain

import java.time.Instant

import com.google.api.services.youtube.{model => google}
import gospeak.libs.youtube.utils.YoutubeParser

import scala.collection.JavaConverters._

final case class YoutubeChannel(id: String,
                                title: String,
                                customUrl: Option[String],
                                description: Option[String],
                                country: Option[String],
                                publishedAt: Instant,
                                categories: List[String],
                                videos: Option[Long],
                                subscribers: Option[Long],
                                views: Option[Long],
                                comments: Option[Long])

object YoutubeChannel {
  // see https://developers.google.com/youtube/v3/docs/channels#resource-representation
  def from(c: google.Channel): Either[YoutubeErrors, YoutubeChannel] = for {
    snippet <- Option(c.getSnippet).toRight(YoutubeErrors(s"Missing snippet for channel ${c.getId}"))
    topics <- Option(c.getTopicDetails).toRight(YoutubeErrors(s"Missing topicDetails for channel ${c.getId}"))
    statistics <- Option(c.getStatistics).toRight(YoutubeErrors(s"Missing statistics for channel ${c.getId}"))
    title <- Option(snippet.getTitle).toRight(YoutubeErrors(s"Missing title for channel ${c.getId}"))
    publishedAt <- Option(snippet.getPublishedAt).map(YoutubeParser.toInstant).toRight(YoutubeErrors(s"Missing publishedAt for channel ${c.getId}"))
  } yield new YoutubeChannel(
    id = c.getId,
    title = title,
    customUrl = Option(snippet.getCustomUrl),
    description = Option(snippet.getDescription),
    country = Option(snippet.getCountry),
    publishedAt = publishedAt,
    categories = Option(topics.getTopicCategories).map(_.asScala.toList).getOrElse(List()),
    videos = Option(statistics.getVideoCount).map(_.longValue()),
    subscribers = Option(statistics.getSubscriberCount).map(_.longValue()),
    views = Option(statistics.getViewCount).map(_.longValue()),
    comments = Option(statistics.getCommentCount).map(_.longValue()))
}
