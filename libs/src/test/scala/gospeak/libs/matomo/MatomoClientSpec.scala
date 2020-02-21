package gospeak.libs.matomo

import gospeak.libs.http.HttpClient
import gospeak.libs.scala.domain.Secret
import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers

class MatomoClientSpec extends AnyFunSpec with Matchers {
  private val conf = MatomoClient.Conf(
    baseUrl = "https://???.matomo.cloud",
    site = 1,
    token = Secret("???"))
  private val client = new MatomoClient(conf)

  ignore("MatomoClient") {
    it("should work") {
      println(client.trackEvent("test", "trackEvent", Some("test4"), None, None).unsafeRunSync())
    }
    it("should fetch page views") {
      // https://gospeak.matomo.cloud/?module=API&method=Actions.getPageUrl&pageUrl=https%3A%2F%2Fgospeak.io%2Fgroups%2Fhumantalks-paris&idSite=1&period=month&date=today&format=JSON&token_auth=???
      // https://gospeak.matomo.cloud/?module=API&method=API.getReportPagesMetadata&pageUrl=https%3A%2F%2Fgospeak.io%2Fgroups%2Fhumantalks-paris&idSite=1&period=month&date=today&format=JSON&token_auth=???
      val params = Seq(
        "module" -> "API",
        "method" -> "VisitsSummary.get",
        "idSite" -> conf.site.toString,
        "period" -> "month",
        "date" -> "today",
        "format" -> "JSON",
        "filter_limit" -> "10",
        "token_auth" -> conf.token.decode
      ).map { case (key, value) => s"$key=$value" }.mkString("&")
      val url = s"${conf.baseUrl}/?$params"
      println(s"url: $url")
      val r = HttpClient.get(url).unsafeRunSync()
      println(r.body)
    }
    it("should fetch best countries") {
      val params = Seq(
        "module" -> "API",
        "method" -> "UserCountry.getCountry",
        "idSite" -> conf.site.toString,
        "period" -> "month",
        "date" -> "today",
        "format" -> "JSON",
        "filter_limit" -> "10",
        "token_auth" -> conf.token.decode
      ).map { case (key, value) => s"$key=$value" }.mkString("&")
      val url = s"${conf.baseUrl}/?$params"
      println(s"url: $url")
      val r = HttpClient.get(url).unsafeRunSync()
      println(r.body)
    }
  }
}
