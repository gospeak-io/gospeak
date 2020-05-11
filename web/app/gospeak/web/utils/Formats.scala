package gospeak.web.utils

import java.time.format.DateTimeFormatter
import java.time.{Instant, LocalDate, LocalDateTime}
import java.util.Locale

import gospeak.core.domain.utils.Constants
import gospeak.core.domain.{Cfp, Event}
import gospeak.infra.services.storage.sql.utils.DoobieUtils.{Filter, Sort}
import gospeak.libs.scala.domain.Page
import gospeak.web.pages.partials.html
import play.api.mvc.{AnyContent, Call, Request}
import play.twirl.api.Html

import scala.annotation.tailrec
import scala.concurrent.duration._

object Formats {
  private val df = DateTimeFormatter.ofPattern("dd MMM YYYY").withZone(Constants.defaultZoneId.normalized()).withLocale(Locale.ENGLISH)
  private val dtf = DateTimeFormatter.ofPattern("dd MMM YYYY 'at' HH:mm '(UTC)'").withZone(Constants.defaultZoneId.normalized()).withLocale(Locale.ENGLISH)
  private val ldtf = DateTimeFormatter.ofPattern("dd MMM YYYY 'at' HH:mm").withLocale(Locale.ENGLISH)
  private val dateFull = DateTimeFormatter.ofPattern("EEEE dd MMMM YYYY").withLocale(Locale.ENGLISH)
  private val time = DateTimeFormatter.ofPattern("HH:mm").withLocale(Locale.ENGLISH)

  def time(d: LocalDateTime): String = time.format(d)

  def date(i: Instant): String = df.format(i)

  def date(d: LocalDateTime): String = df.format(d)

  def date(d: LocalDate): String = df.format(d)

  def datetime(i: Instant): String = dtf.format(i)

  def datetime(d: LocalDateTime): String = ldtf.format(d)

  def dateFull(d: LocalDateTime): String = dateFull.format(d)

  def timeAgo(i: Instant, now: Instant): String = {
    val diffMilli = i.toEpochMilli - now.toEpochMilli
    timeAgo(Duration.fromNanos(1000000 * diffMilli))
  }

  def timeAgo(d: Duration): String = {
    val res =
      if (math.abs(d.toNanos) < 1000) "just now" // displayDuration(d.toNanos, "nanosecond")
      else if (math.abs(d.toMicros) < 1000) "just now" // displayDuration(d.toMicros, "microsecond")
      else if (math.abs(d.toMillis) < 1000) "just now" // displayDuration(d.toMillis, "millisecond")
      else if (math.abs(d.toSeconds) < 60) displayDuration(d.toSeconds, "second")
      else if (math.abs(d.toMinutes) < 60) displayDuration(d.toMinutes, "minute")
      else if (math.abs(d.toHours) < 24) displayDuration(d.toHours, "hour")
      else if (math.abs(d.toDays) < 7) displayDuration(d.toDays, "day")
      else if (math.abs(d.toDays) < 30) displayDuration(d.toDays / 7, "week")
      else if (math.abs(d.toDays) < 365) displayDuration(d.toDays / 30, "month")
      else displayDuration(d.toDays / 365, "year")
    if (res == "just now") res
    else if (res.startsWith("-")) res.stripPrefix("-") + " ago"
    else "in " + res
  }

  private def displayDuration(amount: Long, unit: String): String = {
    val plural = if (math.abs(amount) == 1) "" else "s"
    s"$amount $unit$plural"
  }

  def round(d: Duration): Duration = {
    if (math.abs(d.toNanos) < 1000) Duration(d.toNanos, NANOSECONDS)
    else if (math.abs(d.toMicros) < 1000) Duration(d.toMicros, MICROSECONDS)
    else if (math.abs(d.toMillis) < 1000) Duration(d.toMillis, MILLISECONDS)
    else if (math.abs(d.toSeconds) < 60) Duration(d.toSeconds, SECONDS)
    else if (math.abs(d.toMinutes) < 60) Duration(d.toMinutes, MINUTES)
    else if (math.abs(d.toHours) < 24) Duration(d.toHours, HOURS)
    else Duration(d.toDays, DAYS)
  }

  def duration(d: FiniteDuration): String = {
    def pad(n: Long): String = padLeft(n.toString, 2, '0')

    if (d > 1.hour) s"${d.toHours}:${pad(d.minus(d.toHours.hours).toMinutes)}:${pad(d.minus(d.toMinutes.minutes).toSeconds)}"
    else s"${d.toMinutes}:${pad(d.minus(d.toMinutes.minutes).toSeconds)}"
  }

  @tailrec
  def padLeft(str: String, size: Int, char: Char = ' '): String =
    if (str.length < size) padLeft(char + str, size, char) else str

  def plural(n: Long, word: String, plural: String = ""): String =
    n match {
      case 0 => s"no $word"
      case 1 => s"$n $word"
      case _ if plural.isEmpty => s"$n ${word}s"
      case _ => s"$n $plural"
    }

  def cfpDates(cfp: Cfp)(implicit req: UserReq[AnyContent]): String = (cfp.begin, cfp.close) match {
    case (Some(start), Some(end)) => s"from ${date(start)} to ${date(end)}"
    case (Some(start), None) if start.isAfter(req.nowLDT) => s"starting ${date(start)}"
    case (Some(start), None) => s"started ${date(start)}"
    case (None, Some(end)) if end.isAfter(req.nowLDT) => s"closing ${date(end)}"
    case (None, Some(end)) => s"closed ${date(end)}"
    case (None, None) => "always open"
  }

  def color(kind: Event.Kind): String = kind match {
    case Event.Kind.Conference => "danger"
    case Event.Kind.Meetup => "success"
    case Event.Kind.Training => "primary"
    case Event.Kind.PrivateEvent => "secondary"
  }

  def icon(kind: Event.Kind): Html = kind match {
    case Event.Kind.Conference => Html("<i class=\"fas fa-bullhorn\" title=\"Conference\"></i>")
    case Event.Kind.Meetup => Html("<i class=\"fas fa-calendar-day\" title=\"Meetup\"></i>")
    case Event.Kind.Training => Html("<i class=\"fas fa-book-reader\" title=\"Training\"></i>")
    case Event.Kind.PrivateEvent => Html("<i class=\"fas fa-lock\" title=\"Private event\"></i>")
  }

  def color(kind: Option[Event.Kind]): String = kind.map(color).getOrElse("dark")

  def icon(kind: Option[Event.Kind]): Html = kind.map(icon).getOrElse(Html("<i class=\"fas fa-question\" title=\"Undefined event\"></i>"))

  def mkHtml(list: Seq[Html], sep: Html): Html = list match {
    case Seq() => Html("")
    case Seq(head) => head
    case Seq(head, tail@_*) => Html(tail.foldLeft(new StringBuilder(head.body.trim))((b, html) => b.append(sep.body + html.body.trim)).toString())
  }

  def paginated[A](page: Page[A], link: Page.Params => Call, item: A => Html, filters: Seq[Filter] = Seq(), sorts: Seq[Sort] = Seq()): Html = {
    Html(paginationHeader(page, link, filters, sorts).body + paginationBody(page, item).body + paginationFooter(page, link).body)
  }

  def paginationHeader[A](page: Page[A], link: Page.Params => Call, filters: Seq[Filter], sorts: Seq[Sort]): Html = {
    Html(
      s"""<div class="row"${if (page.params.search.isEmpty && !page.hasManyPages) " style=\"display: none !important;\"" else ""}>
         |  <div class="col-lg-6 col-md-12 mb-3">${html.search(page, link(Page.Params.defaults))}</div>
         |  <div class="col-lg-6 col-md-12 mb-3 d-flex justify-content-end">${html.pagination(page, link)}</div>
         |</div>
         |<div class="mb-3"${if (filters.isEmpty && sorts.isEmpty) " style=\"display: none !important;\"" else ""}>
         |  ${html.filters(page, link, filters)}
         |  ${html.sorts(page, link, sorts)}
         |</div>
       """.stripMargin.trim)
  }

  def paginationBody[A](page: Page[A], item: A => Html): Html = {
    if (page.isEmpty) {
      Html(
        """<div class="jumbotron text-center mb-3">
          |  <h2>No results <i class="far fa-sad-tear"></i></h2>
          |</div>
      """.stripMargin)
    } else {
      Html(
        s"""<div class="list-group mb-3">
           |  ${page.items.map(item).mkString("\n")}
           |</div>
       """.stripMargin)
    }
  }

  def paginationFooter[A](page: Page[A], link: Page.Params => Call): Html = {
    Html(s"""<div class="d-flex justify-content-end mb-3"${if (page.hasManyPages || page.params.search.nonEmpty) "" else " style=\"display: none !important;\""}>${html.pagination(page, link)}</div>""")
  }

  def redirectOr[A](redirect: Option[String], default: => Call)(implicit req: Request[A]): String =
    redirect.filterNot(url => url.contains("login") || url.contains("signup")).getOrElse(default.toString)

  def redirectToPreviousPageOr[A](default: => Call)(implicit req: Request[A]): String =
    redirectOr(HttpUtils.getReferer(req.headers), default)
}
