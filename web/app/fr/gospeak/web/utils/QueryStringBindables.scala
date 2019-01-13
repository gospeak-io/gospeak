package fr.gospeak.web.utils

import fr.gospeak.web.domain._
import play.api.mvc.QueryStringBindable

object QueryStringBindables {
  implicit def pageParamsQueryStringBindable(implicit stringBinder: QueryStringBindable[String], intBinder: QueryStringBindable[Int]): QueryStringBindable[Page.Params] = new QueryStringBindable[Page.Params] {
    override def bind(key: String, params: Map[String, Seq[String]]): Option[Either[String, Page.Params]] =
      Some(for {
        search <- stringBinder.bind("search", params).map(_.map(s => Some(Page.Search(s)))).getOrElse(Right(Page.Params.defaults.search))
        sortBy <- stringBinder.bind("sort-by", params).map(_.map(s => Some(Page.SortBy(s)))).getOrElse(Right(Page.Params.defaults.sortBy))
        page <- intBinder.bind("page", params).map(_.map(p => Page.PageNo(p))).getOrElse(Right(Page.Params.defaults.page))
        pageSize <- intBinder.bind("page-size", params).map(_.map(p => Page.PageSize(p))).getOrElse(Right(Page.Params.defaults.pageSize))
      } yield Page.Params(search, sortBy, page, pageSize))

    override def unbind(key: String, params: Page.Params): String =
      Seq(
        params.search.map(s => stringBinder.unbind("search", s.value)),
        params.sortBy.map(s => stringBinder.unbind("sort-by", s.value)),
        Some(params.page).filter(_ != Page.Params.defaults.page).map(p => intBinder.unbind("page", p.value)),
        Some(params.pageSize).filter(_ != Page.Params.defaults.pageSize).map(p => intBinder.unbind("page-size", p.value))
      ).flatten.mkString("&")
  }
}
