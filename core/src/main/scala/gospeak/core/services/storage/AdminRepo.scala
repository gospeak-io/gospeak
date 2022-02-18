package gospeak.core.services.storage

import cats.effect.IO
import gospeak.core.domain.DbStats

trait AdminRepo {
  def getStats(): IO[DbStats]
}
