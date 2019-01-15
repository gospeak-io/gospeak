package fr.gospeak.core.domain.utils

case class Page[+A](items: Seq[A], params: Page.Params, total: Page.Total) {
  val isLast: Boolean = params.offsetEnd >= total.value
}

object Page {

  case class Search(value: String) extends AnyVal {
    def nonEmpty: Boolean = value.nonEmpty
  }

  case class SortBy(value: String) extends AnyVal {
    def nonEmpty: Boolean = value.nonEmpty
  }

  case class No(value: Int) extends AnyVal {
    //assert(value > 0, "Page.No should be at least 1") // TODO improve
    def nonEmpty: Boolean = value <= Params.defaults.page.value
  }

  case class Size(value: Int) extends AnyVal {
    //assert(value > 0, "Page.Size should be at least 1") // TODO improve
    def nonEmpty: Boolean = value == Params.defaults.pageSize.value
  }

  case class Total(value: Int) extends AnyVal

  case class Params(search: Option[Search], sortBy: Option[SortBy], page: No, pageSize: Size) {
    val offsetStart: Int = (page.value - 1) * pageSize.value
    val offsetEnd: Int = page.value * pageSize.value

  }

  object Params {
    val defaults = Params(None, None, No(1), Size(20))
  }

}
