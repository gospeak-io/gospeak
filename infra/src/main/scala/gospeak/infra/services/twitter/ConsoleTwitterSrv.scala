package gospeak.infra.services.twitter

import cats.effect.IO
import gospeak.core.services.twitter.TwitterSrv
import gospeak.core.services.twitter.domain.Tweet
import org.slf4j.LoggerFactory

class ConsoleTwitterSrv extends TwitterSrv {
  private val logger = LoggerFactory.getLogger(this.getClass)

  override def tweet(msg: String): IO[Tweet] = IO {
    val text = trim(msg)
    logger.info(s"TwitterSrv.tweet($text)")
    Tweet(0, text)
  }
}
