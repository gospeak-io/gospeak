package gospeak.libs.scala.domain

import cats.data.NonEmptyList
import gospeak.libs.scala.Extensions._

final case class Page[+A](items: List[A], params: Page.Params, total: Page.Total) {
  assert(items.length <= params.pageSize.value, s"Page can't have more items (${items.length}) than its size (${params.pageSize.value})")
  private val last: Int = math.ceil(total.value.toDouble / params.pageSize.value).toInt

  def hasManyPages: Boolean = total.value > params.pageSize.value

  def isEmpty: Boolean = total.value == 0

  def nonEmpty: Boolean = !isEmpty

  def isFirst: Boolean = params.page.value == 1

  def isLast: Boolean = params.offsetEnd >= total.value

  def previous: Page.Params = params.copy(page = Page.No(math.max(params.page.value - 1, 1)))

  def next: Page.Params = params.copy(page = Page.No(math.min(params.page.value + 1, last)))

  def isCurrent(i: Page.Params): Boolean = params.page == i.page

  def map[B](f: A => B): Page[B] = Page(items.map(f), params, total)

  private val width = 1
  private val middleStart: Int = (params.page.value - width).max(1)
  private val middleEnd: Int = (params.page.value + width).min(last)

  def firstPages: Option[List[Page.Params]] = if (middleStart > width + 2) Some((1 to width).map(i => params.copy(page = Page.No(i))).toList) else None

  def lastPages: Option[List[Page.Params]] = if (middleEnd < last - width - 1) Some((last - width + 1 to last).map(i => params.copy(page = Page.No(i))).toList) else None

  def middlePages: List[Page.Params] = {
    val start = if (middleStart > width + 2) middleStart else 1
    val end = if (middleEnd < last - width - 1) middleEnd else last
    (start to end).map(i => params.copy(page = Page.No(i))).toList
  }
}

object Page {
  def empty[A]: Page[A] = Page[A](List(), Params.defaults, Total(0))

  def empty[A](params: Params): Page[A] = Page[A](List(), params, Total(0))

  final case class Search(value: String) extends AnyVal {
    def key: String = Search.key

    def nonEmpty: Boolean = value.nonEmpty
  }

  object Search {
    val key = "q"
  }

  final case class OrderBy(values: NonEmptyList[String]) extends AnyVal {
    def prefix(p: String): OrderBy = OrderBy(values.map(v =>
      if (v.contains(".")) v
      else if (v.startsWith("-")) s"-$p.${v.stripPrefix("-")}"
      else s"$p.$v"
    ))

    def key: String = OrderBy.key

    def value: String = values.toList.mkString(",")
  }

  object OrderBy {
    val key = "sort"

    def apply(value: String, other: String*): OrderBy = new OrderBy(NonEmptyList.of(value, other: _*))

    def parse(value: String): Option[OrderBy] =
      value.split(',').map(_.trim).filter(_.nonEmpty).toNel.map(OrderBy(_)).toOption
  }

  // should be at least 1
  final case class No(value: Int) {
    def key: String = No.key

    def nonEmpty: Boolean = value > Params.defaults.page.value
  }

  object No {
    val key = "page"
  }

  // should be at least 1
  final case class Size(value: Int) {
    def key: String = Size.key

    def nonEmpty: Boolean = value != Params.defaults.pageSize.value
  }

  object Size {
    val key = "page-size"

    def from(i: Int): Either[String, Size] = {
      if (i < 1) {
        Left(s"$key should be greater than 1")
      } else if (i > 200) {
        Left(s"$key should be lesser than 200")
      } else {
        Right(Size(i))
      }
    }
  }

  final case class Offset(value: Int)

  final case class Total(value: Long) extends AnyVal

  final case class Params(page: No = Params.defaults.page,
                          pageSize: Size = Params.defaults.pageSize,
                          search: Option[Search] = Params.defaults.search,
                          orderBy: Option[OrderBy] = Params.defaults.orderBy,
                          filters: Map[String, String] = Params.defaults.filters,
                          nullsFirst: Boolean = Params.defaults.nullsFirst) {
    val offset: Offset = Offset((page.value - 1) * pageSize.value)
    val offsetEnd: Int = page.value * pageSize.value

    def defaultSize(size: Int): Params = if (pageSize == Params.defaults.pageSize) copy(pageSize = Size(size)) else this

    def search(q: String): Params = copy(search = Some(Search(q)))

    def sorts: List[String] = orderBy.map(_.values.toList).getOrElse(List())

    def defaultOrderBy(fields: String*): Params = if (orderBy == Params.defaults.orderBy) withOrderBy(fields: _*) else this

    def withOrderBy(fields: String*): Params = copy(orderBy = OrderBy.parse(fields.mkString(",")))

    def withFilter(key: String, value: String): Params = copy(filters = filters + (key -> value))

    def withFilters(f: (String, String)*): Params = copy(filters = f.toMap)

    def dropFilter(key: String): Params = copy(filters = filters - key)

    def toggleFilter(key: String): Params = filters.get(key) match {
      case Some("true") => withFilter(key, "false")
      case Some("false") => withFilter(key, "true")
      case _ => withFilter(key, "true")
    }

    def withNullsFirst: Params = copy(nullsFirst = true)

    def withNullsLast: Params = copy(nullsFirst = false)
  }

  object Params {
    val defaults: Params = Params(No(1), Size(20), None, None, Map(), nullsFirst = false)

    def no(n: Int): Params = defaults.copy(page = No(n))
  }

}
