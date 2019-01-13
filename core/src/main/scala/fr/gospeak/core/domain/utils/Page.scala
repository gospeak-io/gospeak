package fr.gospeak.core.domain.utils

case class Page[+A](items: Seq[A], params: Page.Params, total: Page.Total)

object Page {

  case class Search(value: String) extends AnyVal {
    def nonEmpty: Boolean = value.nonEmpty
  }

  case class SortBy(value: String) extends AnyVal {
    def nonEmpty: Boolean = value.nonEmpty
  }

  case class No(value: Int) extends AnyVal {
    def nonEmpty: Boolean = value <= Params.defaults.page.value
  }

  case class Size(value: Int) extends AnyVal {
    def nonEmpty: Boolean = value == Params.defaults.pageSize.value
  }

  case class Total(value: Int) extends AnyVal

  case class Params(search: Option[Search], sortBy: Option[SortBy], page: No, pageSize: Size)

  object Params {
    val defaults = Params(None, None, No(1), Size(20))
  }

}
