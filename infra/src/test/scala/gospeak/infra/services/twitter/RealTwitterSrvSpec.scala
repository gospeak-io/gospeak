package gospeak.infra.services.twitter

import gospeak.core.services.twitter.TwitterConf
import gospeak.infra.testingutils.BaseSpec
import gospeak.libs.scala.domain.Secret

class RealTwitterSrvSpec extends BaseSpec {
  private val srv = new RealTwitterSrv(TwitterConf.Twitter(
    consumerKey = "...",
    consumerSecret = Secret("..."),
    accessKey = "...",
    accessSecret = Secret("...")))

  ignore("TwitterSrvImpl") {
    it("should twitt") {
      val t = srv.tweet("Hello world!").unsafeRunSync()
      println(t)
      println(s"id: ${t.id}")
    }
  }
}
