package gospeak.libs.youtube.domain

import com.google.api.services.youtube.{model => google}

import scala.collection.JavaConverters._

final case class ChannelsResponse(etag: String,
                                  eventId: Option[String],
                                  items: List[Channel],
                                  kind: String,
                                  nextPageToken: Option[String],
                                  prevPageToken: Option[String],
                                  tokenPagination: Option[google.TokenPagination],
                                  visitorId: Option[String])

object ChannelsResponse {
  def apply(channelListResponse: google.ChannelListResponse): ChannelsResponse =
    new ChannelsResponse(
      etag = channelListResponse.getEtag,
      eventId = Option(channelListResponse.getEventId),
      items = channelListResponse.getItems.asScala.map(Channel(_)).toList,
      kind = channelListResponse.getKind,
      nextPageToken = Option(channelListResponse.getNextPageToken),
      prevPageToken = Option(channelListResponse.getPrevPageToken),
      tokenPagination = Option(channelListResponse.getTokenPagination),
      visitorId = Option(channelListResponse.getVisitorId))
}

final case class Channel(etag: String,
                         id: String,
                         kind: String)

object Channel {
  def apply(channel: google.Channel): Channel =
    new Channel(
      etag = channel.getEtag,
      id = channel.getId,
      kind = channel.getKind)
}
