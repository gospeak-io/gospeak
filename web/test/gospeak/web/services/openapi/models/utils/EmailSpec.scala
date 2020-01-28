package gospeak.web.services.openapi.models.utils

import gospeak.web.services.openapi.error.OpenApiError
import org.scalatest.{FunSpec, Matchers}

class EmailSpec extends FunSpec with Matchers {
  describe("Email") {
    it("should validate correct emails") {
      Email.from("loicknuchel@gmail.com") shouldBe Right(Email("loicknuchel@gmail.com"))
      Email.from("l.k.n+test@pm.md") shouldBe Right(Email("l.k.n+test@pm.md"))
    }
    it("should fail on invalid values") {
      Email.from("abc.test") shouldBe a[Left[_, _]]
      Email.from("abc@test") shouldBe Left(OpenApiError.badFormat("abc@test", "Email", "example@mail.com"))
    }
  }
}
