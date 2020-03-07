package gospeak.core.domain.messages

import java.time.LocalDateTime

final case class MsgExternalCfp(begin: Option[LocalDateTime],
                                close: Option[LocalDateTime],
                                publicLink: String) {
  def isPast(now: LocalDateTime): Boolean = close.exists(_.isBefore(now))

  def isFuture(now: LocalDateTime): Boolean = begin.exists(_.isAfter(now))

  def isActive(now: LocalDateTime): Boolean = !isPast(now) && !isFuture(now)
}
