package gospeak.infra.services.twitter

import cats.effect.IO
import gospeak.core.services.twitter.TwitterSrv
import gospeak.core.services.twitter.domain.Tweet

class TwitterConsoleSrv extends TwitterSrv {
  override def tweet(msg: String): IO[Tweet] = IO {
    val text = trim(msg)
    println(s"TwitterSrv.tweet($text)")
    Tweet(0, text)
  }
}
