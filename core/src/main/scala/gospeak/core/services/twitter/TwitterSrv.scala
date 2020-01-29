package gospeak.core.services.twitter

import cats.effect.IO
import gospeak.core.services.twitter.domain.Tweet

trait TwitterSrv {
  def tweet(msg: String): IO[Tweet]
}
