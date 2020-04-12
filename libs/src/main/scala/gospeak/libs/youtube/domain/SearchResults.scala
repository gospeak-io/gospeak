package gospeak.libs.youtube.domain

import com.google.api.services.youtube.model.{
  PageInfo,
  ResourceId,
  SearchListResponse,
  SearchResultSnippet,
  TokenPagination,
  SearchResult => YSearchResult
}

import scala.collection.JavaConverters._

final case class SearchResults(etag: String,
                               eventId: String,
                               items: Seq[SearchResult],
                               kind: String,
                               nextPageToken: String,
                               prevPageToken: String,
                               pageInfo: PageInfo,
                               tokenPagination: TokenPagination,
                               regionCode: String,
                               visitorId: String,
                              )

object SearchResults {

  private val kind = "youtube#video"

  def apply(response: SearchListResponse): SearchResults =
    new SearchResults(
      response.getEtag,
      response.getEventId,
      response.getItems.asScala
//        .filter(_.getKind == kind)
        .map(SearchResult(_)),
      response.getKind,
      response.getNextPageToken,
      response.getPrevPageToken,
      response.getPageInfo,
      response.getTokenPagination,
      response.getRegionCode,
      response.getVisitorId)
}

final case class SearchResult(etag: String,
                              id: ResourceId,
                              kind: String,
                              snippet: SearchResultSnippet)

object SearchResult {
  def apply(result: YSearchResult): SearchResult =
    new SearchResult(
      result.getEtag,
      result.getId,
      result.getKind,
      result.getSnippet)
}

