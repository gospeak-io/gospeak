package gospeak.libs.scala.domain

import java.time.temporal.ChronoUnit
import java.util.concurrent.TimeUnit

import gospeak.libs.scala.Extensions._
import TimePeriod._

import scala.concurrent.duration.FiniteDuration
import scala.util.{Failure, Success, Try}

// Like Java Period but keeping constructor values
case class TimePeriod(length: Long, unit: PeriodUnit) {
  def toDuration: Try[FiniteDuration] =
    toTime(unit)
      .map(u => Success(new FiniteDuration(length, u)))
      .getOrElse(Try(new FiniteDuration(length * toChrono(unit).getDuration.getSeconds, TimeUnit.SECONDS).toCoarsest))

  def value: String = s"$length $unit"
}

object TimePeriod {
  def from(d: FiniteDuration): TimePeriod =
    TimePeriod(d.length, toPeriod(d.unit))

  def from(str: String): Try[TimePeriod] = {
    str.split(" ") match {
      case Array(length, unit) => for {
        l <- Try(length.toLong)
        u <- PeriodUnit.all.find(_.value == unit).toTry(CustomException(s"No PeriodUnit for '$unit'"))
      } yield TimePeriod(l, u)
      case _ => Failure(CustomException("Invalid format for TimePeriod, expects: 'length unit'"))
    }
  }

  sealed trait PeriodUnit {
    def chrono: ChronoUnit = toChrono(this)

    def value: String = toString

    def plural: String = s"${toString}s"
  }

  object PeriodUnit {

    case object Nano extends PeriodUnit

    case object Micro extends PeriodUnit

    case object Milli extends PeriodUnit

    case object Second extends PeriodUnit

    case object Minute extends PeriodUnit

    case object Hour extends PeriodUnit

    case object Day extends PeriodUnit

    case object Week extends PeriodUnit

    case object Month extends PeriodUnit

    case object Year extends PeriodUnit

    val all: Seq[PeriodUnit] = Seq(Nano, Micro, Milli, Second, Minute, Hour, Day, Week, Month, Year)
    val simple: Seq[PeriodUnit] = Seq(Minute, Hour, Day, Week, Month, Year)
  }

  implicit class TimePeriodBuilder(val value: Long) extends AnyVal {
    def nano: TimePeriod = TimePeriod(value, PeriodUnit.Nano)

    def micro: TimePeriod = TimePeriod(value, PeriodUnit.Micro)

    def milli: TimePeriod = TimePeriod(value, PeriodUnit.Milli)

    def second: TimePeriod = TimePeriod(value, PeriodUnit.Second)

    def minute: TimePeriod = TimePeriod(value, PeriodUnit.Minute)

    def hour: TimePeriod = TimePeriod(value, PeriodUnit.Hour)

    def day: TimePeriod = TimePeriod(value, PeriodUnit.Day)

    def week: TimePeriod = TimePeriod(value, PeriodUnit.Week)

    def month: TimePeriod = TimePeriod(value, PeriodUnit.Month)

    def year: TimePeriod = TimePeriod(value, PeriodUnit.Year)
  }

  private def toChrono(u: PeriodUnit): ChronoUnit = u match {
    case PeriodUnit.Nano => ChronoUnit.NANOS
    case PeriodUnit.Micro => ChronoUnit.MICROS
    case PeriodUnit.Milli => ChronoUnit.MILLIS
    case PeriodUnit.Second => ChronoUnit.SECONDS
    case PeriodUnit.Minute => ChronoUnit.MINUTES
    case PeriodUnit.Hour => ChronoUnit.HOURS
    case PeriodUnit.Day => ChronoUnit.DAYS
    case PeriodUnit.Week => ChronoUnit.WEEKS
    case PeriodUnit.Month => ChronoUnit.MONTHS
    case PeriodUnit.Year => ChronoUnit.YEARS
  }

  private def toTime(u: PeriodUnit): Option[TimeUnit] = u match {
    case PeriodUnit.Nano => Some(TimeUnit.NANOSECONDS)
    case PeriodUnit.Micro => Some(TimeUnit.MICROSECONDS)
    case PeriodUnit.Milli => Some(TimeUnit.MILLISECONDS)
    case PeriodUnit.Second => Some(TimeUnit.SECONDS)
    case PeriodUnit.Minute => Some(TimeUnit.MINUTES)
    case PeriodUnit.Hour => Some(TimeUnit.HOURS)
    case PeriodUnit.Day => Some(TimeUnit.DAYS)
    case _ => None
  }

  private def toPeriod(t: TimeUnit): PeriodUnit = t match {
    case TimeUnit.NANOSECONDS => PeriodUnit.Nano
    case TimeUnit.MICROSECONDS => PeriodUnit.Micro
    case TimeUnit.MILLISECONDS => PeriodUnit.Milli
    case TimeUnit.SECONDS => PeriodUnit.Second
    case TimeUnit.MINUTES => PeriodUnit.Minute
    case TimeUnit.HOURS => PeriodUnit.Hour
    case TimeUnit.DAYS => PeriodUnit.Day
  }
}
