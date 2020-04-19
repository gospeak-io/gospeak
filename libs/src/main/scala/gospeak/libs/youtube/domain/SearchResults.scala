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
  def filter(itemType: String): SearchResults = this.copy(items = items.filter(i => i.id.getKind == itemType))
}

object SearchResults {

  def apply(response: google.SearchListResponse): SearchResults =
    new SearchResults(
      etag = response.getEtag,
      eventId = response.getEventId,
      items = response.getItems
        .asScala
        .map(SearchResult(_)),
      kind = response.getKind,
      nextPageToken = Option(response.getNextPageToken),
      prevPageToken = Option(response.getPrevPageToken),
      pageInfo = Option(response.getPageInfo).map(PageInfo(_)),
      tokenPagination = Option(response.getTokenPagination),
      regionCode = Option(response.getRegionCode),
      visitorId = Option(response.getVisitorId))
}

final case class SearchResult(etag: String,
                              id: google.ResourceId,
                              kind: String,
                              snippet: google.SearchResultSnippet)

object SearchResult {
  def apply(result: google.SearchResult): SearchResult =
    new SearchResult(
      etag = result.getEtag,
      id = result.getId,
      kind = result.getKind,
      snippet = result.getSnippet)
}
