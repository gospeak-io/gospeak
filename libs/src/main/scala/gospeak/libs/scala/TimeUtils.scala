package gospeak.libs.scala

import java.time.{Instant, LocalDateTime, ZoneId}

object TimeUtils {
  def toInstant(date: LocalDateTime, zone: ZoneId): Instant =
    date.toInstant(zone.getRules.getOffset(date))
}
