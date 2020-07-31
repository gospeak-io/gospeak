package gospeak.libs.http

import gospeak.libs.testingutils.BaseSpec

class HttpClientSpec extends BaseSpec {
  private val http = new HttpClientImpl

  ignore("HttpClient") {
    it("should perform a GET request") {
      val res = http.get("http://www.mocky.io/v2/5db57f2c320000860018bf8a").unsafeRunSync()
      res.body shouldBe """{"status": "ok"}"""
    }
  }
}
