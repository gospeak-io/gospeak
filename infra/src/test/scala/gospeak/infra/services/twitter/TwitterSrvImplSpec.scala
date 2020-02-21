package gospeak.infra.services.twitter

import gospeak.core.services.twitter.TwitterConf
import gospeak.libs.scala.domain.Secret
import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers

class TwitterSrvImplSpec extends AnyFunSpec with Matchers {
  private val srv = new TwitterSrvImpl(TwitterConf(
    consumerKey = "...",
    consumerSecret = Secret("..."),
    accessKey = "...",
    accessSecret = Secret("...")), performWriteOps = false)

  ignore("TwitterSrvImpl") {
    it("should twitt") {
      val t = srv.tweet("Hello world!").unsafeRunSync()
      println(t)
      println(s"id: ${t.id}")
    }
  }
}
