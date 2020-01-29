package gospeak.infra.services.twitter

import cats.effect.{ContextShift, IO}
import com.danielasfregola.twitter4s.TwitterRestClient
import com.danielasfregola.twitter4s.entities.{AccessToken, ConsumerToken, Tweet}
import gospeak.core.services.twitter.{TwitterConf, TwitterSrv, domain => gs}
import org.slf4j.LoggerFactory

import scala.concurrent.ExecutionContext

class TwitterSrvImpl(conf: TwitterConf, performWriteOps: Boolean) extends TwitterSrv {
  private val logger = LoggerFactory.getLogger(this.getClass)
  implicit private val cs: ContextShift[IO] = IO.contextShift(ExecutionContext.global)
  private val consumerToken = ConsumerToken(key = conf.consumerKey, secret = conf.consumerSecret.decode)
  private val accessToken = AccessToken(key = conf.accessKey, secret = conf.accessSecret.decode)
  private val restClient = TwitterRestClient(consumerToken, accessToken)
  private val limit = 280

  def tweet(msg: String): IO[gs.Tweet] = {
    val text = if (msg.length > limit) msg.take(limit - 3) + "..." else msg
    if (performWriteOps) {
      IO.fromFuture(IO(restClient.createTweet(text))).map(fromLib)
    } else {
      logger.info(s"TwitterSrvImpl.tweet($msg)")
      IO.pure(gs.Tweet(1L, text))
    }
  }

  private def fromLib(t: Tweet): gs.Tweet =
    gs.Tweet(
      id = t.id,
      text = t.text)
}
