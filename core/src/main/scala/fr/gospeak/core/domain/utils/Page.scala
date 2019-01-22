package fr.gospeak.core.domain.utils

final case class Page[+A](items: Seq[A], params: Page.Params, total: Page.Total) {
  assert(items.length <= params.pageSize.value, s"Page can't have more items (${items.length}) than its size (${params.pageSize.value})")
  private val last: Int = math.ceil(total.value.toDouble / params.pageSize.value).toInt
  val hasManyPages: Boolean = total.value > params.pageSize.value
  val isFirst: Boolean = params.page.value == 1
  val isLast: Boolean = params.offsetEnd >= total.value
  val previous: Page.Params = params.copy(page = Page.No(math.max(params.page.value - 1, 1)))
  val next: Page.Params = params.copy(page = Page.No(math.min(params.page.value + 1, last)))

  def isCurrent(i: Page.Params): Boolean = params.page == i.page

  def map[B](f: A => B): Page[B] = Page(items.map(f), params, total)

  private val width = 3
  private val middleStart: Int = (params.page.value - width).max(1)
  private val middleEnd: Int = (params.page.value + width).min(last)

  def firstPages: Option[Seq[Page.Params]] = if (middleStart > width + 2) Some((1 to width).map(i => params.copy(page = Page.No(i)))) else None

  def lastPages: Option[Seq[Page.Params]] = if (middleEnd < last - width - 1) Some((last - width + 1 to last).map(i => params.copy(page = Page.No(i)))) else None

  def middlePages: Seq[Page.Params] = {
    val start = if (middleStart > width + 2) middleStart else 1
    val end = if (middleEnd < last - width - 1) middleEnd else last
    (start to end).map(i => params.copy(page = Page.No(i)))
  }
}

object Page {

  final case class Search(value: String) extends AnyVal {
    def key: String = Search.key

    def nonEmpty: Boolean = value.nonEmpty
  }

  object Search {
    val key = "q"
  }

  final case class OrderBy(value: String) extends AnyVal {
    def key: String = OrderBy.key

    def nonEmpty: Boolean = value.nonEmpty
  }

  object OrderBy {
    val key = "sort"
  }

  // should be at least 1
  final case class No(value: Int) extends AnyVal {
    def key: String = No.key

    def nonEmpty: Boolean = value > Params.defaults.page.value

    def +(v: Int): No = No(value + 1)

    def -(v: Int): No = No(math.max(value - 1, 1))
  }

  object No {
    val key = "page"
  }

  // should be at least 1
  final case class Size(value: Int) extends AnyVal {
    def key: String = Size.key

    def nonEmpty: Boolean = value != Params.defaults.pageSize.value
  }

  object Size {
    val key = "page-size"
  }

  final case class Total(value: Long) extends AnyVal

  final case class Params(page: No = Params.defaults.page,
                          pageSize: Size = Params.defaults.pageSize,
                          search: Option[Search] = Params.defaults.search,
                          orderBy: Option[OrderBy] = Params.defaults.orderBy) {
    val offsetStart: Int = (page.value - 1) * pageSize.value
    val offsetEnd: Int = page.value * pageSize.value
  }

  object Params {
    val defaults = Params(No(1), Size(20), None, None)
  }

}
