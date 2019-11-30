package fr.gospeak.infra.utils

import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers

class HttpClientSpec extends AnyFunSpec with Matchers {
  ignore("HttpClient") {
    it("should perform a GET request") {
      val res = HttpClient.get("http://www.mocky.io/v2/5db57f2c320000860018bf8a").unsafeRunSync()
      res.body shouldBe """{"status": "ok"}"""
    }
  }
}
