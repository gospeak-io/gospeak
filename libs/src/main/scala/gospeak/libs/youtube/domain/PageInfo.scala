package gospeak.libs.youtube.domain

import com.google.api.services.youtube.{model => google}

final case class PageInfo(resultsPerPage: Int, totalResults: Int)

object PageInfo {
  def apply(pageInfo: google.PageInfo): PageInfo =
    new PageInfo(pageInfo.getResultsPerPage,
      pageInfo.getTotalResults)
}

