package gospeak.libs.openapi.models

import gospeak.libs.openapi.error.OpenApiError
import gospeak.libs.openapi.error.OpenApiError.ErrorMessage
import gospeak.libs.openapi.models.utils.Version
import org.scalatest.{FunSpec, Matchers}

class OpenApiSpec extends FunSpec with Matchers {
  private val basicOpenApi = OpenApi(Version(1, 2, 3), Info("My api", None, None, None, None, Version(1), None), None, None, None, None, None, Map(), None)

  describe("OpenApi") {
    it("should check duplicated paths") {
      val item = PathItem(None, None, None, None, None, None, None, None, None, None, None, None, None)
      OpenApi.checkDuplicatedPaths(Map()) shouldBe List()
      OpenApi.checkDuplicatedPaths(Map(
        Path("/api/{id}") -> item,
        Path("/api/{value}") -> item
      )) shouldBe List(
        OpenApiError.duplicateValue("/api/{value}").atPath("./api/{value}")
      )
    }
    it("should check duplicated operationIds") {
      val op1 = PathItem.Operation(None, Some("id1"), None, None, None, None, None, None, Map(), None, None, None, None)
      val op2 = PathItem.Operation(None, Some("id2"), None, None, None, None, None, None, Map(), None, None, None, None)
      val item1 = PathItem(None, None, None, None, get = Some(op1), put = Some(op2), post = Some(op2), None, None, None, None, None, None)
      val item2 = PathItem(None, None, None, None, get = Some(op1), None, None, None, None, None, None, None, None)
      OpenApi.checkDuplicatedOperationIds(Map(
        Path("/api1") -> item1,
        Path("/api2") -> item2
      )) shouldBe List(
        OpenApiError.duplicateValue("id1").atPath("./api2", ".get"),
        OpenApiError.duplicateValue("id2").atPath("./api1", ".put"))
    }
    it("should group errors by path") {
      OpenApi.groupErrors(List(
        OpenApiError.duplicateValue("1").atPath("a", "b"),
        OpenApiError.duplicateValue("2").atPath("a", "c"),
        OpenApiError.duplicateValue("3").atPath("a", "b")
      )) shouldBe List(
        OpenApiError(ErrorMessage.duplicateValue("1"), ErrorMessage.duplicateValue("3")).atPath("a", "b"),
        OpenApiError.duplicateValue("2").atPath("a", "c")
      )
    }

    /*
    it("should fail when missing a variable") {
      val json = Json.parse("""{"url": "https://gospeak.io:{port}/api"}""")
      json.validate[Server] shouldBe JsError(ErrorMessage.missingVariable("port").toJson)
    }
    it("should fail if ArrayVal examples have the wrong type") {
      val json1 = Json.parse("""{"type": "array", "items": {"type": "string"}, "example": [2]}""")
      json1.validate[Schema.ArrayVal] shouldBe JsError(ErrorMessage.badExampleFormat("2", "string", "").toJson)

      val json2 = Json.parse(
        """{
          |  "schemas": {
          |    "Id": {"type": "string"},
          |    "Tmp": {"$ref": "#/components/schemas/Id"},
          |    "Tags": {"type": "array", "items": {"$ref": "#/components/schemas/Tmp"}, "example": [2]}
          |  }
          |}""".stripMargin)
      json2.validate[Components] shouldBe JsError(ErrorMessage.badExampleFormat("2", "string", "Tags").toJson)
    }
    it("should fail if ObjectVal has duplicate value in required") {
      val json = Json.parse("""{"type": "object", "properties": {"id": {"type": "string"}}, "required": ["id", "id"]}""")
      json.validate[Schema.ObjectVal] shouldBe JsError(ErrorMessage.duplicateValue("id", "required").toJson)
    }
    it("should fail if ObjectVal has a required field not present in properties") {
      val json = Json.parse("""{"type": "object", "properties": {}, "required": ["id"]}""")
      json.validate[Schema.ObjectVal] shouldBe JsError(ErrorMessage.missingProperty("id", "required").toJson)
    }
    */
  }
}
