package gospeak.infra.services.twitter

import cats.effect.{ContextShift, IO}
import com.danielasfregola.twitter4s.TwitterRestClient
import com.danielasfregola.twitter4s.entities.{AccessToken, ConsumerToken, Tweet}
import gospeak.core.services.twitter.{TwitterConf, TwitterSrv, domain => gs}

import scala.concurrent.ExecutionContext

class RealTwitterSrv(conf: TwitterConf.Twitter) extends TwitterSrv {
  implicit private val cs: ContextShift[IO] = IO.contextShift(ExecutionContext.global)
  private val consumerToken = ConsumerToken(key = conf.consumerKey, secret = conf.consumerSecret.decode)
  private val accessToken = AccessToken(key = conf.accessKey, secret = conf.accessSecret.decode)
  private val restClient = TwitterRestClient(consumerToken, accessToken)

  def tweet(msg: String): IO[gs.Tweet] =
    IO.fromFuture(IO(restClient.createTweet(trim(msg)))).map(fromLib)

  private def fromLib(t: Tweet): gs.Tweet =
    gs.Tweet(
      id = t.id,
      text = t.text)
}
