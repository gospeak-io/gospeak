package gospeak.core.services.storage

import cats.effect.IO
import gospeak.core.domain.DbStats

import java.time.Instant

trait AdminRepo {
  def getStats(since: Option[Instant]): IO[DbStats]
}
