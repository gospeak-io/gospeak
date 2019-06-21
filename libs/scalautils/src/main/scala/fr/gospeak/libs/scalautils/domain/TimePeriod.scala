package fr.gospeak.libs.scalautils.domain

import java.time.temporal.ChronoUnit
import java.util.concurrent.TimeUnit

import fr.gospeak.libs.scalautils.Extensions._
import fr.gospeak.libs.scalautils.domain.TimePeriod._

import scala.concurrent.duration.FiniteDuration
import scala.util.{Failure, Success, Try}

// Like Java Period but keeping constructor values
case class TimePeriod(length: Long, unit: ChronoUnit) {
  def toDuration: Try[FiniteDuration] =
    toTime(unit)
      .map(u => Success(new FiniteDuration(length, u)))
      .getOrElse(Try(new FiniteDuration(length * unit.getDuration.getSeconds, TimeUnit.SECONDS).toCoarsest))

  def value: String = if (length == 1) s"$length $unit".stripSuffix("s") else s"$length $unit"
}

object TimePeriod {
  def from(d: FiniteDuration): TimePeriod =
    TimePeriod(d.length, toChrono(d.unit))

  def from(str: String): Try[TimePeriod] = {
    str.split(" ") match {
      case Array(length, unit) => for {
        l <- Try(length.toLong)
        u <- ChronoUnit.values().find(_.toString.startsWith(unit)).toTry(CustomException(s"No enum constant java.time.temporal.ChronoUnit for '$unit'"))
      } yield TimePeriod(l, u)
      case _ => Failure(CustomException("Invalid format for TimePeriod, expects: 'length unit'"))
    }
  }

  private def toChrono(t: TimeUnit): ChronoUnit = t match {
    case TimeUnit.NANOSECONDS => ChronoUnit.NANOS
    case TimeUnit.MICROSECONDS => ChronoUnit.MICROS
    case TimeUnit.MILLISECONDS => ChronoUnit.MILLIS
    case TimeUnit.SECONDS => ChronoUnit.SECONDS
    case TimeUnit.MINUTES => ChronoUnit.MINUTES
    case TimeUnit.HOURS => ChronoUnit.HOURS
    case TimeUnit.DAYS => ChronoUnit.DAYS
  }

  private def toTime(c: ChronoUnit): Option[TimeUnit] = c match {
    case ChronoUnit.NANOS => Some(TimeUnit.NANOSECONDS)
    case ChronoUnit.MICROS => Some(TimeUnit.MICROSECONDS)
    case ChronoUnit.MILLIS => Some(TimeUnit.MILLISECONDS)
    case ChronoUnit.SECONDS => Some(TimeUnit.SECONDS)
    case ChronoUnit.MINUTES => Some(TimeUnit.MINUTES)
    case ChronoUnit.HOURS => Some(TimeUnit.HOURS)
    case ChronoUnit.DAYS => Some(TimeUnit.DAYS)
    case _ => None
  }
}
