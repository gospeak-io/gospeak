package gospeak.web.services.openapi.models

import gospeak.web.services.openapi.error.OpenApiError
import org.scalatest.{FunSpec, Matchers}

class ReferenceSpec extends FunSpec with Matchers {
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
