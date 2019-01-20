package fr.gospeak.core.domain.utils

case class Page[+A](items: Seq[A], params: Page.Params, total: Page.Total) {
  val hasManyPages: Boolean = total.value > params.pageSize.value
  val isFirst: Boolean = params.page.value == 1
  val isLast: Boolean = params.offsetEnd >= total.value
  val previous: Page.Params = params.copy(page = params.page - 1)
  val next: Page.Params = params.copy(page = params.page + 1)

  def isCurrent(i: Page.No): Boolean = params.page == i

  def no(i: Page.No): Page.Params = params.copy(page = i)

  def map[B](f: A => B): Page[B] = Page(items.map(f), params, total)

  private val width = 3
  private val lastPage: Int = math.ceil(total.value.toDouble / params.pageSize.value).toInt
  private val middleStart: Int = (params.page.value - width).max(1)
  private val middleEnd: Int = (params.page.value + width).min(lastPage)
  val firstPages: Option[Seq[Page.No]] = if (middleStart > width + 2) Some((1 to width).map(Page.No(_))) else None
  val lastPages: Option[Seq[Page.No]] = if (middleEnd < lastPage - width - 1) Some((lastPage - width + 1 to lastPage).map(Page.No(_))) else None
  val middlePages: Seq[Page.No] = {
    val start = if (middleStart > width + 2) middleStart else 1
    val end = if (middleEnd < lastPage - width - 1) middleEnd else lastPage
    (start to end).map(Page.No(_))
  }
}

object Page {

  case class Search(value: String) extends AnyVal {
    def key: String = Search.key

    def nonEmpty: Boolean = value.nonEmpty
  }

  object Search {
    val key = "q"
  }

  case class OrderBy(value: String) extends AnyVal {
    def key: String = OrderBy.key

    def nonEmpty: Boolean = value.nonEmpty
  }

  object OrderBy {
    val key = "sort"
  }

  // should be at least 1
  case class No(value: Int) extends AnyVal {
    def key: String = No.key

    def nonEmpty: Boolean = value > Params.defaults.page.value

    def +(v: Int): No = No(value + 1)

    def -(v: Int): No = No(value - 1)
  }

  object No {
    val key = "page"
  }

  // should be at least 1
  case class Size(value: Int) extends AnyVal {
    def key: String = Size.key

    def nonEmpty: Boolean = value != Params.defaults.pageSize.value
  }

  object Size {
    val key = "page-size"
  }

  case class Total(value: Int) extends AnyVal

  case class Params(search: Option[Search], orderBy: Option[OrderBy], page: No, pageSize: Size) {
    val offsetStart: Int = (page.value - 1) * pageSize.value
    val offsetEnd: Int = page.value * pageSize.value

  }

  object Params {
    val defaults = Params(None, None, No(1), Size(20))
  }

}
