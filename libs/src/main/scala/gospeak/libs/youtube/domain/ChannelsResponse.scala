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
                         kind: String,
                         contentDetails: Option[google.ChannelContentDetails],
                         conversionPings: Option[google.ChannelConversionPings],
                         brandingSettings: Option[google.ChannelBrandingSettings],
                         auditDetails: Option[google.ChannelAuditDetails],
                         snippet: Option[google.ChannelSnippet],
                         statistics: Option[google.ChannelStatistics],
                         status: Option[google.ChannelStatus],
                         invideoPromotion: Option[google.InvideoPromotion],
                         topicDetails: Option[google.ChannelTopicDetails],
                         localizations: Option[Map[String, google.ChannelLocalization]])

object Channel {
  def apply(channel: google.Channel): Channel =
    new Channel(
      etag = channel.getEtag,
      id = channel.getId,
      kind = channel.getKind,
      contentDetails = Option(channel.getContentDetails),
      conversionPings = Option(channel.getConversionPings),
      brandingSettings = Option(channel.getBrandingSettings),
      auditDetails = Option(channel.getAuditDetails),
      snippet = Option(channel.getSnippet),
      statistics = Option(channel.getStatistics),
      status = Option(channel.getStatus),
      invideoPromotion = Option(channel.getInvideoPromotion),
      topicDetails = Option(channel.getTopicDetails),
      localizations = Option(channel.getLocalizations).map(_.asScala.toMap))
}
