package gospeak.libs.http

import gospeak.libs.testingutils.BaseSpec

class HttpClientSpec extends BaseSpec {
  private val http = new HttpClientImpl

  describe("HttpClient") {
    it("should build url") {
      HttpClient.buildUrl("https://www.youtube.com/watch", Map("v" -> "5n0rJb2BVW8", "t" -> "15")) shouldBe "https://www.youtube.com/watch?v=5n0rJb2BVW8&t=15"
      HttpClient.buildUrl("https://www.youtube.com/watch?v=5n0rJb2BVW8", Map("t" -> "15")) shouldBe "https://www.youtube.com/watch?v=5n0rJb2BVW8&t=15"
    }
  }
  ignore("HttpClientImpl") {
    it("should perform a GET request") {
      val res = http.get("http://www.mocky.io/v2/5db57f2c320000860018bf8a").unsafeRunSync()
      res.body shouldBe """{"status": "ok"}"""
    }
  }
}
