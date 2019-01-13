package fr.gospeak.web.domain

case class Page[+A](items: Seq[A], params: Page.Params, total: Page.Total)

object Page {

  case class Search(value: String) extends AnyVal

  case class SortBy(value: String) extends AnyVal

  case class PageNo(value: Int) extends AnyVal

  case class PageSize(value: Int) extends AnyVal

  case class Total(value: Int) extends AnyVal

  case class Params(search: Option[Search], sortBy: Option[SortBy], page: PageNo, pageSize: PageSize)

  object Params {
    val defaults = Params(None, None, PageNo(1), PageSize(20))
  }

}
