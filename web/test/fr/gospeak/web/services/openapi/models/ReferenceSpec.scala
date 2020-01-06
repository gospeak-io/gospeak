package fr.gospeak.web.services.openapi.models

import cats.data.NonEmptyList
import fr.gospeak.web.services.openapi.error.OpenApiError.ErrorMessage
import org.scalatest.{FunSpec, Matchers}

class ReferenceSpec extends FunSpec with Matchers {
  describe("Reference") {
    it("should validate correct References") {
      Reference.from("#/components/schemas/Pet") shouldBe Right(Reference("#/components/schemas/Pet"))
      Reference.from("Pet.json") shouldBe Right(Reference("Pet.json"))
      Reference.from("definitions.json#/Pet") shouldBe Right(Reference("definitions.json#/Pet"))
    }
    it("should fail on invalid values") {
      Reference.from("#wrong#value") shouldBe Left(NonEmptyList.of(ErrorMessage.badFormat("#wrong#value", "Reference", "#/components/.../...")))
    }
  }
}
