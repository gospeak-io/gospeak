package gospeak.web

import java.time.format.DateTimeFormatter
import java.time.{Instant, LocalDate, LocalDateTime}
import java.util.Locale

import gospeak.core.domain._
import gospeak.core.domain.utils.Constants
import gospeak.libs.scala.domain._
import gospeak.libs.sql.doobie.Table.{Filter, Sort}
import io.circe.Encoder
import play.api.data.{Form, FormError}
import play.api.mvc.{AnyContent, Call, Flash}
import play.twirl.api.Html

import scala.annotation.tailrec
import scala.concurrent.duration._
import scala.util.Try

/**
 * This package object is useful to automatically import implicit classes used for formatting in templates.
 * Many formatting methods should take an implicit BasicReq[AnyContent] as parameter for later i18n (lang, dates, distances, weights...)
 */
package object utils {
  private val df = DateTimeFormatter.ofPattern("dd MMM YYYY").withZone(Constants.defaultZoneId.normalized()).withLocale(Locale.ENGLISH)
  private val dtf = DateTimeFormatter.ofPattern("dd MMM YYYY 'at' HH:mm '(UTC)'").withZone(Constants.defaultZoneId.normalized()).withLocale(Locale.ENGLISH)
  private val ldtf = DateTimeFormatter.ofPattern("dd MMM YYYY 'at' HH:mm").withLocale(Locale.ENGLISH)
  private val dateFull = DateTimeFormatter.ofPattern("EEEE dd MMMM YYYY").withLocale(Locale.ENGLISH)
  private val time = DateTimeFormatter.ofPattern("HH:mm").withLocale(Locale.ENGLISH)

  /**
   * Formatters for basic Scala/Play classes
   */

  implicit class RichInstant(val i: Instant) extends AnyVal {
    def asDate(implicit req: BasicReq[AnyContent]): String = df.format(i)

    def asDatetime(implicit req: BasicReq[AnyContent]): String = dtf.format(i)

    def asTimeAgo(implicit req: BasicReq[AnyContent]): Html = {
      val duration = Duration.fromNanos(1000000 * (i.toEpochMilli - req.now.toEpochMilli))
      Html(s"""<span title="${i.asDatetime}" data-toggle="tooltip">${timeAgo(duration)}</span>""")
    }
  }

  implicit class RichLocalDate(val d: LocalDate) extends AnyVal {
    def asDate(implicit req: BasicReq[AnyContent]): String = df.format(d)
  }

  implicit class RichLocalDateTime(val d: LocalDateTime) extends AnyVal {
    def asDate(implicit req: BasicReq[AnyContent]): String = df.format(d)

    def asDateFull(implicit req: BasicReq[AnyContent]): String = dateFull.format(d)

    def asDatetime(implicit req: BasicReq[AnyContent]): String = ldtf.format(d)

    def asTime(implicit req: BasicReq[AnyContent]): String = time.format(d)
  }

  implicit class RichFiniteDuration(val d: FiniteDuration) extends AnyVal {
    def round: FiniteDuration = {
      if (math.abs(d.toNanos) < 1000) FiniteDuration(d.toNanos, NANOSECONDS)
      else if (math.abs(d.toMicros) < 1000) FiniteDuration(d.toMicros, MICROSECONDS)
      else if (math.abs(d.toMillis) < 1000) FiniteDuration(d.toMillis, MILLISECONDS)
      else if (math.abs(d.toSeconds) < 60) FiniteDuration(d.toSeconds, SECONDS)
      else if (math.abs(d.toMinutes) < 60) FiniteDuration(d.toMinutes, MINUTES)
      else if (math.abs(d.toHours) < 24) FiniteDuration(d.toHours, HOURS)
      else FiniteDuration(d.toDays, DAYS)
    }

    def asTime(implicit req: BasicReq[AnyContent]): String = {
      if (d > 1.hour) s"${d.toHours}:${pad(d.minus(d.toHours.hours).toMinutes)}:${pad(d.minus(d.toMinutes.minutes).toSeconds)}"
      else s"${d.toMinutes}:${pad(d.minus(d.toMinutes.minutes).toSeconds)}"
    }

    def asBadge(implicit req: BasicReq[AnyContent]): Html = Html(s"""<span class="badge badge-primary">${d.toString}</span>""")

    def asBadge(title: String)(implicit req: BasicReq[AnyContent]): Html = Html(s"""<span class="badge badge-primary" title="$title">${d.toString}</span>""")

    private def pad(n: Long): String = padLeft(n.toString, 2, '0')

    @tailrec
    private def padLeft(str: String, size: Int, char: Char = ' '): String =
      if (str.length < size) padLeft(char + str, size, char) else str
  }

  implicit class RichString(val s: String) extends AnyVal {
    def asCompanyItem: Html = Html(s"""<span class="company-link"><i class="fas fa-sitemap" title="Company"></i> $s</span>""")

    def asLocationItem: Html = Html(s"""<span class="location-link"><i class="fas fa-map-marker-alt" title="Location"></i> $s</span>""")

    def asPhoneItemLink: Html = Html(s"""<span class="phone-link"><i class="fas fa-phone" title="Phone"></i> <a href="tel:$s" class="no-style" target="_blank">$s</a></span>""")

    def asExcerpt: String = if (s.length > 200) s.take(197) + "..." else s
  }

  implicit class RichLong(val l: Long) extends AnyVal {
    def plural(word: String, plural: String = "")(implicit req: BasicReq[AnyContent]): String = l match {
      case 0 => s"no $word"
      case 1 => s"$l $word"
      case _ if plural.isEmpty => s"$l ${word}s"
      case _ => s"$l $plural"
    }
  }

  implicit class RichInt(val i: Int) extends AnyVal {
    def plural(word: String, plural: String = "")(implicit req: BasicReq[AnyContent]): String = (i: Long).plural(word, plural)
  }

  implicit class RichList[A](val s: List[A]) extends AnyVal {
    def plural(word: String, plural: String = "")(implicit req: BasicReq[AnyContent]): String = (s.length: Long).plural(word, plural)
  }

  implicit class RichListHtml(val l: List[Html]) extends AnyVal {
    def mkHtml(sep: Html): Html = l match {
      case List() => Html("")
      case List(head) => head
      case List(head, tail@_*) => Html(tail.foldLeft(new StringBuilder(head.body.trim))((b, html) => b.append(sep.body + html.body.trim)).toString())
    }

    def mkHtml(sep: String): Html = mkHtml(Html(sep))

    def mkHtml: Html = mkHtml(Html(""))
  }

  implicit class RichCall(val c: Call) extends AnyVal {
    def previousPageOrThis(implicit req: BasicReq[AnyContent]): String = redirectOr(HttpUtils.getReferer(req.headers), c)

    private def redirectOr(redirect: Option[String], default: => Call)(implicit req: BasicReq[AnyContent]): String =
      redirect.filterNot(url => url.contains("login") || url.contains("signup")).getOrElse(default.toString)
  }

  implicit class RichForm[A](val in: Form[A]) extends AnyVal {
    def flash(implicit req: BasicReq[AnyContent]): Flash = Flash(in.data ++ in.errors.headOption.map(_ => "error" -> errors))

    private def errors(implicit req: BasicReq[AnyContent]): String = in.errors.map(e => s"${e.key}: ${req.format(e)}<br>").mkString("\n")
  }

  implicit class RichFormMap[V](val in: Map[String, V]) extends AnyVal {
    def eitherGet(key: String): Either[FormError, V] =
      in.get(key).map(Right(_)).getOrElse(Left(FormError(key, Mappings.requiredError)))

    def eitherGet[A](key: String, parse: V => Try[A], err: => String): Either[FormError, A] =
      eitherGet(key).flatMap(v => parse(v).toEither.left.map(e => FormError(key, err, e.getMessage)))
  }

  /**
   * Formatters for utils classes
   */

  implicit class RichUrl(val u: Url) extends AnyVal {
    def asWebsiteItemLink: Html = Html(s"""<span class="website-link"><i class="fas fa-globe" title="Website"></i> <a href="${u.value}" class="no-style" target="_blank">${u.value}</a></span>""")
  }

  implicit class RichUrlOpt(val ou: Option[Url]) extends AnyVal {
    def asWebsiteIconLink: Html = ou.map(u => Html(s"""<a href="${u.value}" target="_blank" title="Site"><i class="fas fa-link"></i></a>""")).getOrElse(Html("""<i class="fas fa-link text-muted"></i>"""))
  }

  implicit class RichUrlVideoOpt(val ou: Option[Url.Video]) extends AnyVal {
    def asIconLink: Html = ou.map(u => Html(s"""<a href="${u.value}" target="_blank" title="Video"><i class="fab fa-youtube"></i></a>""")).getOrElse(Html("""<i class="fab fa-youtube text-muted"></i>"""))
  }

  implicit class RichUrlSlidesOpt(val ou: Option[Url.Slides]) extends AnyVal {
    def asIconLink: Html = ou.map(u => Html(s"""<a href="${u.value}" target="_blank" title="Slides"><i class="fab fa-slideshare"></i></a>""")).getOrElse(Html("""<i class="fab fa-slideshare text-muted"></i>"""))
  }

  implicit class RichEmailAddress(val e: EmailAddress) extends AnyVal {
    def asItemLink: Html = Html(s"""<span class="email-link"><i class="fas fa-envelope" title="Email"></i> <a href="mailto:${e.value}" class="no-style" target="_blank">${e.value}</a></span>""")
  }

  implicit class RichGMapPlace(val p: GMapPlace) extends AnyVal {
    def asText: String = p.formatted

    def asLink: Html = Html(s"""<a href="${p.url}" target="_blank">${p.formatted}</a>""")
  }

  implicit class RichListTag(val l: List[Tag]) extends AnyVal {
    def asBadges: Html = asBadges()

    def asBadges(color: String = "primary", title: String = "tag"): Html =
      Html(l.sortBy(_.value).map(t => s"""<span class="badge badge-$color" title="$title">${t.value}</span>""").mkString(" "))

    def asBadgeLinks(link: Tag => Call, color: String = "primary", title: String = "tag"): Html =
      Html(l.sortBy(_.value).map(t => s"""<a href="${link(t)}" class="badge badge-$color" title="$title">${t.value}</a>""").mkString(" "))
  }

  implicit class RichMarkdown(val m: Markdown) {
    def render: Html = Html(m.toHtml.value)

    def asExcerpt: String = m.toText.asExcerpt
  }

  implicit class RichLiquidMarkdown[A](val m: LiquidMarkdown[A]) {
    def asExcerpt(a: A)(implicit e: Encoder[A]): String = m.render(a).fold(_ => Markdown(m.value), identity).asExcerpt
  }

  implicit class RichPage[A](val p: Page[A]) extends AnyVal {
    def plural(word: String, plural: String = "")(implicit req: BasicReq[AnyContent]): String = p.total.value.plural(word, plural)

    def render(link: Page.Params => Call, filters: List[Filter] = List(), sorts: List[Sort] = List())(item: A => Html): Html =
      Html(List(
        paginationHeader(link, filters, sorts),
        paginationBody(item),
        paginationFooter(link)).map(_.body).mkString)

    def renderCustom(link: Page.Params => Call, filters: List[Filter] = List(), sorts: List[Sort] = List())(header: Page[A] => Html)(empty: Html)(item: A => Html)(footer: Page[A] => Html): Html =
      Html(List(
        paginationHeader(link, filters, sorts),
        header(p),
        if (p.isEmpty) empty else Html(p.items.map(item).mkString("\n")),
        footer(p),
        paginationFooter(link)).map(_.body).mkString)

    private def paginationHeader(link: Page.Params => Call, filters: List[Filter], sorts: List[Sort]): Html = {
      import gospeak.web.pages.partials.html
      Html(
        s"""<div class="row"${if (p.params.search.isEmpty && !p.hasManyPages) " style=\"display: none !important;\"" else ""}>
           |  <div class="col-lg-6 col-md-12 mb-3">${html.search(p, link(Page.Params.defaults))}</div>
           |  <div class="col-lg-6 col-md-12 mb-3 d-flex justify-content-end">${html.pagination(p, link)}</div>
           |</div>
           |<div class="mb-3"${if (filters.isEmpty && sorts.isEmpty) " style=\"display: none !important;\"" else ""}>
           |  ${html.filters(p, link, filters)}
           |  ${html.sorts(p, link, sorts)}
           |</div>
       """.stripMargin.trim)
    }

    private def paginationBody(item: A => Html): Html = {
      if (p.isEmpty) {
        Html(
          """<div class="jumbotron text-center mb-3">
            |  <h2>No results <i class="far fa-sad-tear"></i></h2>
            |</div>
      """.stripMargin)
      } else {
        Html(
          s"""<div class="list-group mb-3">
             |  ${p.items.map(item).mkString("\n")}
             |</div>
       """.stripMargin)
      }
    }

    private def paginationFooter(link: Page.Params => Call): Html = {
      import gospeak.web.pages.partials.html
      Html(s"""<div class="d-flex justify-content-end mb-3"${if (p.hasManyPages || p.params.search.nonEmpty) "" else " style=\"display: none !important;\""}>${html.pagination(p, link)}</div>""")
    }
  }

  /**
   * Formatters for domain classes
   */

  implicit class RichUserId(val id: User.Id) extends AnyVal {
    def asBadge(users: List[User]): Html = users.find(_.id == id) match {
      case Some(u) => Html(s"""<span class="badge badge-pill badge-primary">${u.name.value}</span>""")
      case None => Html(s"""<span class="badge badge-pill badge-primary">Unknown user (${id.value})</span>""")
    }

    def asBadgeLink(users: List[User], link: User => Option[Call] = _ => None): Html = users.find(_.id == id).map(u => (u, link(u))) match {
      case Some((u, Some(url))) => Html(s"""<a href="$url" class="badge badge-pill badge-primary">${u.name.value}</a>""")
      case Some((u, None)) => Html(s"""<span class="badge badge-pill badge-primary">${u.name.value}</span>""")
      case None => Html(s"""<span class="badge badge-pill badge-primary">Unknown user (${id.value})</span>""")
    }
  }

  implicit class RichCfp(val c: Cfp) extends AnyVal {
    def asDates(implicit req: UserReq[AnyContent]): String = (c.begin, c.close) match {
      case (Some(start), Some(end)) => s"from ${start.asDate} to ${end.asDate}"
      case (Some(start), None) if start.isAfter(req.nowLDT) => s"starting ${start.asDate}"
      case (Some(start), None) => s"started ${start.asDate}"
      case (None, Some(end)) if end.isAfter(req.nowLDT) => s"closing ${end.asDate}"
      case (None, Some(end)) => s"closed ${end.asDate}"
      case (None, None) => "always open"
    }
  }

  implicit class RichEventId(val id: Event.Id) extends AnyVal {
    def asName(events: List[Event]): Html = events.find(_.id == id) match {
      case Some(e) => Html(s"""<span>${e.name.value}</span>""")
      case None => Html(s"""<span>Unknown (${id.value})</span>""")
    }

    def asLink(events: List[Event], link: Event => Call)(implicit req: UserReq[AnyContent]): Html = events.find(_.id == id) match {
      case Some(e) => Html(s"""<a href="${link(e)}" title="on ${e.start.asDate}">${e.name.value}</a>""")
      case None => Html(s"""<span>Unknown (${id.value})</span>""")
    }
  }

  implicit class RichEventKind(val k: Event.Kind) extends AnyVal {
    def asColor: String = k match {
      case Event.Kind.Conference => "danger"
      case Event.Kind.Meetup => "success"
      case Event.Kind.Training => "primary"
      case Event.Kind.PrivateEvent => "secondary"
    }

    def asIcon: Html = k match {
      case Event.Kind.Conference => Html("<i class=\"fas fa-bullhorn\" title=\"Conference\"></i>")
      case Event.Kind.Meetup => Html("<i class=\"fas fa-calendar-day\" title=\"Meetup\"></i>")
      case Event.Kind.Training => Html("<i class=\"fas fa-book-reader\" title=\"Training\"></i>")
      case Event.Kind.PrivateEvent => Html("<i class=\"fas fa-lock\" title=\"Private event\"></i>")
    }
  }

  implicit class RichEventKindOpt(val k: Option[Event.Kind]) extends AnyVal {
    def asColor: String = k.map(_.asColor).getOrElse("dark")

    def asIcon(implicit req: BasicReq[AnyContent]): Html = k.map(_.asIcon).getOrElse(Html("<i class=\"fas fa-question\" title=\"Undefined event\"></i>"))
  }

  implicit class RichTalkStatus(val s: Talk.Status) extends AnyVal {
    def asBadge(implicit req: BasicReq[AnyContent]): Html = s match {
      case Talk.Status.Public => Html(s"""<span class="badge badge-primary">${s.value}</span>""")
      case Talk.Status.Private => Html(s"""<span class="badge badge-secondary">${s.value}</span>""")
      case Talk.Status.Archived => Html(s"""<span class="badge badge-danger">${s.value}</span>""")
    }
  }

  implicit class RichProposalStatus(val s: Proposal.Status) extends AnyVal {
    def asBadge(implicit req: BasicReq[AnyContent]): Html = s match {
      case Proposal.Status.Pending => Html(s"""<span class="badge badge-primary">${s.value}</span>""")
      case Proposal.Status.Accepted => Html(s"""<span class="badge badge-success">${s.value}</span>""")
      case Proposal.Status.Declined => Html(s"""<span class="badge badge-danger">${s.value}</span>""")
    }

    def asIcon(implicit req: BasicReq[AnyContent]): Html = s match {
      case Proposal.Status.Pending => Html(s"""<span class="text-primary" title="${s.value}"><i class="fas fa-clock"></i></span>""")
      case Proposal.Status.Accepted => Html(s"""<span class="text-success" title="${s.value}"><i class="fas fa-check"></i></span>""")
      case Proposal.Status.Declined => Html(s"""<span class="text-danger" title="${s.value}"><i class="far fa-times-circle"></i></span>""")
    }
  }

  /**
   * Private functions used in formatters
   */

  private[utils] def timeAgo(d: Duration)(implicit req: BasicReq[AnyContent]): String = {
    def displayDuration(amount: Long, unit: String): String = {
      val plural = if (math.abs(amount) == 1) "" else "s"
      s"$amount $unit$plural"
    }

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

}
