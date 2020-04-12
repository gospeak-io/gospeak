package gospeak.libs.youtube.domain
import scala.collection.JavaConverters._

import com.google.api.services.youtube.model.{
  PlaylistItemContentDetails,
  PlaylistItemListResponse,
  PlaylistItemStatus,
  TokenPagination,
  PlaylistItem => GPlaylistItem
}

final case class PlaylistItems(etag: String,
                               eventId: String,
                               items: Seq[PlaylistItem],
                               kind: String,
                               nextPageToken: String,
                               prevPageToken: String,
                               tokenPagination: TokenPagination,
                               visitorId: String)

object PlaylistItems {
  def apply(response: PlaylistItemListResponse): PlaylistItems =
    new PlaylistItems(response.getEtag,
      response.getEventId,
      response.getItems.asScala.map(PlaylistItem(_)),
      response.getKind,
      response.getNextPageToken,
      response.getPrevPageToken,
      response.getTokenPagination,
      response.getVisitorId)
}

final case class PlaylistItem(contentDetails: PlaylistItemContentDetails,
                              etag: String,
                              id: String,
                              kind: String,
                              status: PlaylistItemStatus)

object PlaylistItem {
  def apply(playlist: GPlaylistItem): PlaylistItem = {
    new PlaylistItem(playlist.getContentDetails,
      playlist.getEtag,
      playlist.getId,
      playlist.getKind,
      playlist.getStatus)
  }
}
