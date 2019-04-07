package fr.gospeak.web.utils

import java.time.format.DateTimeFormatter
import java.time.{Instant, LocalDateTime, ZoneOffset}
import java.util.Locale

import scala.concurrent.duration._

object Formats {
  private val df = DateTimeFormatter.ofPattern("dd MMM YYYY").withZone(ZoneOffset.UTC.normalized()).withLocale(Locale.ENGLISH)
  private val dtf = DateTimeFormatter.ofPattern("dd MMM YYYY 'at' HH:mm:ss.SSS '(UTC)'").withZone(ZoneOffset.UTC.normalized()).withLocale(Locale.ENGLISH)
  private val ldtf = DateTimeFormatter.ofPattern("dd MMM YYYY 'at' HH:mm:ss").withLocale(Locale.ENGLISH)

  def date(i: Instant): String = df.format(i)

  def date(d: LocalDateTime): String = df.format(d)

  def datetime(i: Instant): String = dtf.format(i)

  def datetime(d: LocalDateTime): String = ldtf.format(d)

  def timeAgo(i: Instant, now: Instant): String = {
    val diffMilli = i.toEpochMilli - now.toEpochMilli
    timeAgo(Duration.fromNanos(1000000 * diffMilli))
  }

  def timeAgo(d: Duration): String = {
    val res =
      if (math.abs(d.toNanos) < 1000) displayDuration(d.toNanos, "nanosecond")
      else if (math.abs(d.toMicros) < 1000) displayDuration(d.toMicros, "microsecond")
      else if (math.abs(d.toMillis) < 1000) displayDuration(d.toMillis, "millisecond")
      else if (math.abs(d.toSeconds) < 60) displayDuration(d.toSeconds, "second")
      else if (math.abs(d.toMinutes) < 60) displayDuration(d.toMinutes, "minute")
      else if (math.abs(d.toHours) < 24) displayDuration(d.toHours, "hour")
      else if (math.abs(d.toDays) < 7) displayDuration(d.toDays, "day")
      else if (math.abs(d.toDays) < 30) displayDuration(d.toDays / 7, "week")
      else if (math.abs(d.toDays) < 365) displayDuration(d.toDays / 30, "month")
      else displayDuration(d.toDays / 365, "year")
    if (res.startsWith("-")) res.stripPrefix("-") + " ago"
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
}
