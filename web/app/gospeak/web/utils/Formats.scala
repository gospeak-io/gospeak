package gospeak.web.utils

import java.time.format.DateTimeFormatter
import java.time.{Instant, LocalDate, LocalDateTime}
import java.util.Locale

import gospeak.core.domain.{Cfp, Event}
import gospeak.core.domain.utils.Constants
import gospeak.infra.services.storage.sql.utils.DoobieUtils.Filter
import gospeak.web.pages.partials.html
import gospeak.libs.scala.domain.Page
import play.api.mvc.{AnyContent, Call}
import play.twirl.api.Html

import scala.concurrent.duration._

object Formats {
  private val df = DateTimeFormatter.ofPattern("dd MMM YYYY").withZone(Constants.defaultZoneId.normalized()).withLocale(Locale.ENGLISH)
  private val dtf = DateTimeFormatter.ofPattern("dd MMM YYYY 'at' HH:mm:ss.SSS '(UTC)'").withZone(Constants.defaultZoneId.normalized()).withLocale(Locale.ENGLISH)
  private val ldtf = DateTimeFormatter.ofPattern("dd MMM YYYY 'at' HH:mm:ss").withLocale(Locale.ENGLISH)
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
    case Event.Kind.PrivateEvent => "dark"
  }

  def mkHtml(list: Seq[Html], sep: Html): Html = list match {
    case Seq() => Html("")
    case Seq(head) => head
    case Seq(head, tail@_*) => Html(tail.foldLeft(new StringBuilder(head.body.trim))((b, html) => b.append(sep.body + html.body.trim)).toString())
  }

  def paginated[A](page: Page[A], link: Page.Params => Call, item: A => Html, filters: Seq[Filter] = Seq()): Html = {
    Html(paginationHeader(page, link, filters).body + paginationBody(page, item).body + paginationFooter(page, link).body)
  }

  def paginationHeader[A](page: Page[A], link: Page.Params => Call, filters: Seq[Filter]): Html = {
    Html(
      s"""<div class="d-flex justify-content-between align-items-center mb-3"${if (page.hasManyPages || page.params.search.nonEmpty) "" else " style=\"display: none !important;\""}>
         |  ${html.search(page, link(Page.Params.defaults))}
         |  ${html.pagination(page, link)}
         |</div>
         |${html.filters(page, link, filters)}
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
    Html(s"""<div class="d-flex justify-content-end mb-3">${html.pagination(page, link)}</div>""")
  }
}
