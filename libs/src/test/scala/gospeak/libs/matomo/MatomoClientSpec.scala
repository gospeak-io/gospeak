package gospeak.libs.matomo

import gospeak.libs.scala.domain.Secret
import org.scalatest.{FunSpec, Matchers}

class MatomoClientSpec extends FunSpec with Matchers {
  private val client = new MatomoClient(MatomoClient.Conf(
    baseUrl = "https://???.matomo.cloud",
    site = 1,
    token = Secret("???")))

  ignore("MatomoClient") {
    it("should work") {
      println(client.trackEvent("test", "trackEvent", Some("test4"), None, None).unsafeRunSync())
    }
  }
}
