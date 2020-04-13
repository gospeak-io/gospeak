package gospeak.libs.youtube.domain

import java.time.Instant

import scala.collection.JavaConverters._
import com.google.api.services.youtube.model.{PlaylistItemContentDetails, PlaylistItemListResponse, PlaylistItemStatus, TokenPagination, PlaylistItem => GPlaylistItem}

final case class PlaylistItems(etag: String,
                               eventId: Option[String],
                               items: List[PlaylistItem],
                               kind: String,
                               nextPageToken: Option[String],
                               prevPageToken: Option[String],
                               tokenPagination: Option[TokenPagination],
                               visitorId: Option[String])

object PlaylistItems {
  def apply(response: PlaylistItemListResponse): PlaylistItems =
    new PlaylistItems(response.getEtag,
      Option(response.getEventId),
      response.getItems.asScala.map(PlaylistItem(_)).toList,
      response.getKind,
      Option(response.getNextPageToken),
      Option(response.getPrevPageToken),
      Option(response.getTokenPagination),
      Option(response.getVisitorId))
}

final case class PlaylistItem(contentDetails: Option[ContentDetails],
                              etag: String,
                              id: String,
                              kind: String,
                              status: Option[PlaylistItemStatus])

object PlaylistItem {
  def apply(playlist: GPlaylistItem): PlaylistItem = {
    new PlaylistItem(
      Option(playlist.getContentDetails).map(ContentDetails(_)),
      playlist.getEtag,
      playlist.getId,
      playlist.getKind,
      Option(playlist.getStatus))
  }
}


final case class ContentDetails(endAt: Option[String],
                                note: Option[String],
                                startAt: Option[String],
                                videoId: Option[String],
                                videoPublished: Option[Instant])

object ContentDetails {

  def apply(details: PlaylistItemContentDetails): ContentDetails =
    new ContentDetails(Option(details.getEndAt),
      Option(details.getNote),
      Option(details.getStartAt),
      Option(details.getVideoId),
      Option(details.getVideoPublishedAt).map(d => Instant.ofEpochMilli(d.getValue)))
}
