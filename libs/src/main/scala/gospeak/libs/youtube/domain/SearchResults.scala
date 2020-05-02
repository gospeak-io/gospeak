package gospeak.libs.youtube.domain

import com.google.api.services.youtube.{model => google}

import scala.collection.JavaConverters._

final case class SearchResults(etag: String,
                               eventId: String,
                               items: Seq[SearchResult],
                               kind: String,
                               nextPageToken: Option[String],
                               prevPageToken: Option[String],
                               pageInfo: Option[PageInfo],
                               tokenPagination: Option[google.TokenPagination],
                               regionCode: Option[String],
                               visitorId: Option[String]) {
  def filter(itemType: String): SearchResults = this.copy(items = items.filter(i => i.id.kind == itemType))

  def hasNextPage: Boolean = nextPageToken.isDefined

  def itemIds: Seq[String] = items.map(i => i.id.id)
}

object SearchResults {

  def apply(response: google.SearchListResponse): SearchResults =
    new SearchResults(
      etag = response.getEtag,
      eventId = response.getEventId,
      items = response.getItems.asScala.map(SearchResult(_)),
      kind = response.getKind,
      nextPageToken = Option(response.getNextPageToken),
      prevPageToken = Option(response.getPrevPageToken),
      pageInfo = Option(response.getPageInfo).map(PageInfo(_)),
      tokenPagination = Option(response.getTokenPagination),
      regionCode = Option(response.getRegionCode),
      visitorId = Option(response.getVisitorId))
}

final case class SearchResult(etag: String,
                              id: ResourceId,
                              kind: String,
                              snippet: google.SearchResultSnippet)

object SearchResult {
  def apply(result: google.SearchResult): SearchResult =
    new SearchResult(
      etag = result.getEtag,
      id = ResourceId(result.getId),
      kind = result.getKind,
      snippet = result.getSnippet)
}

final case class ResourceId(kind: String, id: String)

object ResourceId {
  def apply(resourceId: google.ResourceId): ResourceId = {
    val id = Option(resourceId.getChannelId)
      .orElse(Option(resourceId.getPlaylistId))
      .getOrElse(resourceId.getVideoId)
    new ResourceId(resourceId.getKind, id)
  }
}
