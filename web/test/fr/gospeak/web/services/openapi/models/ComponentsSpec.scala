package fr.gospeak.web.services.openapi.models

import fr.gospeak.web.services.openapi.OpenApiFactory.Formats._
import fr.gospeak.web.services.openapi.error.OpenApiError.ErrorMessage
import fr.gospeak.web.utils.Extensions._
import org.scalatest.{FunSpec, Matchers}
import play.api.libs.json.{JsError, Json}

class ComponentsSpec extends FunSpec with Matchers {
  describe("Components") {
    describe("Schema") {
      it("should fail ArrayVal examples have the wrong type") {
        val json = Json.parse(
          """{
            |  "schemas": {
            |    "Id": {"type": "string"},
            |    "Tmp": {"$ref": "#/components/schemas/Id"},
            |    "Tags": {"type": "array", "items": {"$ref": "#/components/schemas/Tmp"}, "example": [2]}
            |  }
            |}""".stripMargin)
        json.validate[Components] shouldBe JsError(ErrorMessage.badExampleFormat("2", "string", "Tags").toJson)
      }
    }
  }
}
