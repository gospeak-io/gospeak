package gospeak.libs.openapi.models

import gospeak.libs.openapi.error.OpenApiError
import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers

class ReferenceSpec extends AnyFunSpec with Matchers {
  describe("Reference") {
    it("should validate correct References") {
      Reference.from("#/components/schemas/Pet") shouldBe Right(Reference.schema("Pet"))
      Reference.from("Pet.json") shouldBe Right(Reference("Pet.json"))
      Reference.from("definitions.json#/Pet") shouldBe Right(Reference("definitions.json#/Pet"))
    }
    it("should fail on invalid values") {
      Reference.from("#wrong#value") shouldBe Left(OpenApiError.badFormat("#wrong#value", "Reference", "#/components/.../..."))
    }
  }
}
