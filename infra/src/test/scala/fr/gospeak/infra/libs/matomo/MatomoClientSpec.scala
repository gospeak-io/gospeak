package fr.gospeak.infra.libs.matomo

import gospeak.core.services.matomo.MatomoConf
import gospeak.libs.scala.domain.Secret
import org.scalatest.{FunSpec, Matchers}

class MatomoClientSpec extends FunSpec with Matchers {
  private val client = new MatomoClient(MatomoConf(
    baseUrl = "https://???.matomo.cloud",
    site = 1,
    token = Secret("???")))

  ignore("MatomoClient") {
    it("should work") {
      println(client.trackEvent("test", "trackEvent", Some("test4"), None, None).unsafeRunSync())
    }
  }
}
