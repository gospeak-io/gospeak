package fr.gospeak.web.services.openapi.models.utils

import cats.data.NonEmptyList
import fr.gospeak.web.services.openapi.error.OpenApiError.ErrorMessage
import org.scalatest.{FunSpec, Matchers}

class UrlSpec extends FunSpec with Matchers {
  describe("Url") {
    it("should validate correct urls") {
      Url.from("https://gospeak.io") shouldBe Right(Url("https://gospeak.io"))
      Url.from("http://gospeak.io/toto.json?q=test#h2") shouldBe Right(Url("http://gospeak.io/toto.json?q=test#h2"))
    }
    it("should fail on invalid values") {
      Url.from("abc") shouldBe Left(NonEmptyList.of(ErrorMessage.badFormat("abc", "Url", "https://...")))
    }
  }
}
