package gospeak.core.services.twitter

import cats.effect.IO
import gospeak.core.services.twitter.domain.Tweet

trait TwitterSrv {
  private val limit = 280

  def tweet(msg: String): IO[Tweet]

  protected def trim(msg: String): String = if (msg.length > limit) msg.take(limit - 3) + "..." else msg
}
