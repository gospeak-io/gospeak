package gospeak.libs.youtube.domain

import java.time.Instant

import com.google.api.services.youtube.{model => google}

import scala.collection.JavaConverters._

final case class PlaylistItems(etag: String,
                               eventId: Option[String],
                               items: List[PlaylistItem],
                               kind: String,
                               nextPageToken: Option[String],
                               prevPageToken: Option[String],
                               tokenPagination: Option[google.TokenPagination],
                               visitorId: Option[String])

object PlaylistItems {
  def apply(response: google.PlaylistItemListResponse): PlaylistItems =
    new PlaylistItems(
      etag = response.getEtag,
      eventId = Option(response.getEventId),
      items = response.getItems.asScala.map(PlaylistItem(_)).toList,
      kind = response.getKind,
      nextPageToken = Option(response.getNextPageToken),
      prevPageToken = Option(response.getPrevPageToken),
      tokenPagination = Option(response.getTokenPagination),
      visitorId = Option(response.getVisitorId))
}

final case class PlaylistItem(contentDetails: Option[ContentDetails],
                              etag: String,
                              id: String,
                              kind: String,
                              status: Option[google.PlaylistItemStatus])

object PlaylistItem {
  def apply(playlist: google.PlaylistItem): PlaylistItem =
    new PlaylistItem(
      contentDetails = Option(playlist.getContentDetails).map(ContentDetails(_)),
      etag = playlist.getEtag,
      id = playlist.getId,
      kind = playlist.getKind,
      status = Option(playlist.getStatus))
}

final case class ContentDetails(endAt: Option[String],
                                note: Option[String],
                                startAt: Option[String],
                                videoId: Option[String],
                                videoPublished: Option[Instant])

object ContentDetails {
  def apply(details: google.PlaylistItemContentDetails): ContentDetails =
    new ContentDetails(
      endAt = Option(details.getEndAt),
      note = Option(details.getNote),
      startAt = Option(details.getStartAt),
      videoId = Option(details.getVideoId),
      videoPublished = Option(details.getVideoPublishedAt).map(d => Instant.ofEpochMilli(d.getValue)))
}
