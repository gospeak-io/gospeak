package gospeak.libs.scala

import java.time._
import scala.concurrent.duration.{FiniteDuration, MILLISECONDS, NANOSECONDS}
import scala.util.Try

object TimeUtils {
  def parseInstant(str: String): Try[Instant] =
    Try(Instant.parse(str))
      .orElse(Try(LocalDateTime.parse(str)).map(toInstant))
      .orElse(Try(LocalDate.parse(str)).map(toInstant))

  def toInstant(date: LocalDateTime): Instant = toInstant(date, ZoneOffset.UTC)

  def toInstant(date: LocalDateTime, zone: ZoneId): Instant = date.toInstant(zone.getRules.getOffset(date))

  def toInstant(date: LocalDate): Instant = toInstant(date.atStartOfDay())

  def toInstant(date: LocalDate, zone: ZoneId): Instant = toInstant(date.atStartOfDay(), zone)

  def toLocalDateTime(i: Instant, zone: ZoneId = ZoneOffset.UTC): LocalDateTime = LocalDateTime.ofInstant(i, zone)

  def toLocalDate(i: Instant, zone: ZoneId = ZoneOffset.UTC): LocalDate = LocalDate.ofInstant(i, zone)

  def toFiniteDuration(duration: String): Try[FiniteDuration] =
    Try(Duration.parse(duration)).map(v => FiniteDuration(v.toNanos, NANOSECONDS).toCoarsest)

  def toFiniteDuration(start: Instant, end: Instant): FiniteDuration =
    FiniteDuration(end.toEpochMilli - start.toEpochMilli, MILLISECONDS).toCoarsest
}
