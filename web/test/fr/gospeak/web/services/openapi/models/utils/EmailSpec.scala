package fr.gospeak.web.services.openapi.models.utils

import org.scalatest.{FunSpec, Matchers}

class EmailSpec extends FunSpec with Matchers {
  describe("Email") {
    it("should validate correct emails") {
      Email.from("loicknuchel@gmail.com") shouldBe Right(Email("loicknuchel@gmail.com"))
      Email.from("l.k.n+test@pm.md") shouldBe Right(Email("l.k.n+test@pm.md"))
    }
    it("should fail on invalid values") {
      Url.from("abc.test") shouldBe a[Left[_, _]]
      Url.from("abc@test") shouldBe a[Left[_, _]]
    }
  }
}
