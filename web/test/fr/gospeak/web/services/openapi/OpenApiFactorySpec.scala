package fr.gospeak.web.services.openapi

import fr.gospeak.web.services.openapi.error.OpenApiError.{Message, ValidationError}
import fr.gospeak.web.services.openapi.error.OpenApiErrors
import fr.gospeak.web.services.openapi.models.{OpenApi, Version}
import org.scalatest.{FunSpec, Matchers}
import play.api.libs.json.Json

class OpenApiFactorySpec extends FunSpec with Matchers {
  describe("OpenApiFactory") {
    it("should parse a simple OpenApi spec") {
      val json = Json.parse(
        """{
          |  "openapi": "3.0.2"
          |}""".stripMargin)
      val openApi = OpenApi(Version(3, 0, 2))
      OpenApiFactory.parseJson(json) shouldBe Right(openApi)
      OpenApiFactory.toJson(openApi) shouldBe json
    }
    it("should return required key errors") {
      OpenApiFactory.parseJson(Json.parse("{}")) shouldBe Left(OpenApiErrors(
        ValidationError(List(".openapi"), List(Message("error.path.missing")))
      ))
    }
    it("should return bad format errors") {
      OpenApiFactory.parseJson(Json.parse("""{"openapi": 1}""")) shouldBe Left(OpenApiErrors(
        ValidationError(List(".openapi"), List(Message("error.expected.jsstring")))
      ))
    }
    it("should return bad validation errors") {
      OpenApiFactory.parseJson(Json.parse("""{"openapi": "a"}""")) shouldBe Left(OpenApiErrors(
        ValidationError(List(".openapi"), List(Message.validationFailed("a", "x.y.z", "Version")))
      ))
    }
  }
}
