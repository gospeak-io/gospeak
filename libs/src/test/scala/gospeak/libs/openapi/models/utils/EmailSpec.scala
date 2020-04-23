package gospeak.libs.openapi.models.utils

import gospeak.libs.openapi.error.OpenApiError
import gospeak.libs.testingutils.BaseSpec

class EmailSpec extends BaseSpec {
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
