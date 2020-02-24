package gospeak.libs.scala.domain

import org.scalatest.{FunSpec, Matchers}

class UrlSpec extends FunSpec with Matchers {
  private val full = "http://sub.domain.ext/path/to/file?p1=value&p2=other#fragment"

  describe("Url") {
    it("should build only valid url") {
      Url.from("") shouldBe a[Left[_, _]]
      Url.from("test") shouldBe a[Left[_, _]]

      Url.from("http://example") shouldBe a[Right[_, _]]
      Url.from("http://example.fr") shouldBe a[Right[_, _]]
      Url.from(full) shouldBe a[Right[_, _]]
    }
  }
}
