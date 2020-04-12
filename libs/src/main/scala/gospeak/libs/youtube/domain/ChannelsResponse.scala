package gospeak.libs.youtube.domain

import com.google.api.services.youtube.model.{ChannelAuditDetails, ChannelBrandingSettings, ChannelContentDetails, ChannelConversionPing, ChannelConversionPings, ChannelListResponse, ChannelLocalization, ChannelSnippet, ChannelStatistics, ChannelStatus, ChannelTopicDetails, InvideoPromotion, TokenPagination, Channel => YChannel}

import scala.collection.JavaConverters._

final case class ChannelsResponse(etag: String,
                                  eventId: Option[String],
                                  items: List[Channel],
                                  kind: String,
                                  nextPageToken: Option[String],
                                  prevPageToken: Option[String],
                                  tokenPagination: Option[TokenPagination],
                                  visitorId: Option[String])

object ChannelsResponse {

  def apply(channelListResponse: ChannelListResponse): ChannelsResponse = {
    new ChannelsResponse(
      channelListResponse.getEtag,
      Option(channelListResponse.getEventId),
      channelListResponse.getItems.asScala.map(Channel(_)).toList,
      channelListResponse.getKind,
      Option(channelListResponse.getNextPageToken),
      Option(channelListResponse.getPrevPageToken),
      Option(channelListResponse.getTokenPagination),
      Option(channelListResponse.getVisitorId)
    )
  }
}

final case class Channel(etag: String,
                         id: String,
                         kind: String,
                         contentDetails: Option[ChannelContentDetails],
                         conversionPings: Option[ChannelConversionPings],
                         brandingSettings: Option[ChannelBrandingSettings],
                         auditDetails: Option[ChannelAuditDetails],
                         snippet: Option[ChannelSnippet],
                         statistics: Option[ChannelStatistics],
                         status: Option[ChannelStatus],
                         invideoPromotion: Option[InvideoPromotion],
                         topicDetails: Option[ChannelTopicDetails],
                         localizations: Option[Map[String, ChannelLocalization]]
                        )

object Channel {
  def apply(channel: YChannel): Channel = {
    new Channel(
      channel.getEtag,
      channel.getId,
      channel.getKind,
      Option(channel.getContentDetails),
      Option(channel.getConversionPings),
      Option(channel.getBrandingSettings),
      Option(channel.getAuditDetails),
      Option(channel.getSnippet),
      Option(channel.getStatistics),
      Option(channel.getStatus),
      Option(channel.getInvideoPromotion),
      Option(channel.getTopicDetails),
      Option(channel.getLocalizations).map(_.asScala.toMap),
    )
  }
}
