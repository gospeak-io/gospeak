package fr.gospeak.web.services.openapi

import fr.gospeak.web.services.openapi.error.OpenApiError.{Message, ValidationError}
import fr.gospeak.web.services.openapi.error.OpenApiErrors
import fr.gospeak.web.services.openapi.models.utils._
import fr.gospeak.web.services.openapi.models.{Info, OpenApi}
import org.scalatest.{FunSpec, Matchers}
import play.api.libs.json.{JsSuccess, Json}

class OpenApiFactorySpec extends FunSpec with Matchers {
  describe("OpenApiFactory") {
    it("should parse a simple OpenApi spec") {
      val json = Json.parse(
        """{
          |  "openapi": "3.0.2",
          |  "info": {
          |    "title": "My api",
          |    "version": "1.0.0"
          |  }
          |}""".stripMargin)
      val openApi = OpenApi(
        Version(3, 0, 2),
        Info("My api", None, None, None, None, Version(1), Option.empty[TODO]),
        Option.empty[TODO])
      OpenApiFactory.parseJson(json) shouldBe Right(openApi)
      OpenApiFactory.toJson(openApi) shouldBe json
    }
    it("should parse a complex OpenApi spec") {
      val json = Json.parse(
        """{
          |  "openapi": "3.0.2",
          |  "info": {
          |    "title": "My api",
          |    "description": "desc",
          |    "termsOfService": "https://gospeak.io/tos",
          |    "contact": {"name": "Support", "url": "https://gospeak.io/support", "email": "contact@gospeak.io"},
          |    "license": {"name": "Apache 2.0", "url": "https://gospeak.io/license"},
          |    "version": "1.0.0"
          |  }
          |}""".stripMargin)
      val openApi = OpenApi(
        Version(3, 0, 2),
        Info(
          "My api",
          Some(Markdown("desc")),
          Some(Url("https://gospeak.io/tos")),
          Some(Info.Contact(Some("Support"), Some(Url("https://gospeak.io/support")), Some(Email("contact@gospeak.io")), Option.empty[TODO])),
          Some(Info.License("Apache 2.0", Some(Url("https://gospeak.io/license")), Option.empty[TODO])),
          Version(1),
          Option.empty[TODO]),
        Option.empty[TODO])
      OpenApiFactory.parseJson(json) shouldBe Right(openApi)
      OpenApiFactory.toJson(openApi) shouldBe json
    }
    it("should return required key errors") {
      OpenApiFactory.parseJson(Json.parse("{}")) shouldBe Left(OpenApiErrors(
        ValidationError(List(".openapi"), List(Message("error.path.missing"))),
        ValidationError(List(".info"), List(Message("error.path.missing")))
      ))
    }
    it("should return bad format errors") {
      OpenApiFactory.parseJson(Json.parse(
        """{
          |  "openapi": 1,
          |  "info": "a"
          |}""".stripMargin)) shouldBe Left(OpenApiErrors(
        ValidationError(List(".openapi"), List(Message("error.expected.jsstring"))),
        ValidationError(List(".info"), List(Message("error.expected.jsobject")))
      ))
    }
    it("should return bad validation errors") {
      OpenApiFactory.parseJson(Json.parse(
        """{
          |  "openapi": "a",
          |  "info": {"title": "t", "version": "b"}
          |}""".stripMargin)) shouldBe Left(OpenApiErrors(
        ValidationError(List(".openapi"), List(Message.validationFailed("a", "x.y.z", "Version"))),
        ValidationError(List(".info", ".version"), List(Message.validationFailed("b", "x.y.z", "Version")))
      ))
    }
    describe("Info") {
      it("should parse a full featured info") {
        import OpenApiFactory.Formats._
        val json = Json.parse(
          """{
            |  "title": "My api",
            |  "description": "desc",
            |  "termsOfService": "https://gospeak.io/tos",
            |  "contact": {"name": "Support", "url": "https://gospeak.io/support", "email": "contact@gospeak.io"},
            |  "license": {"name": "Apache 2.0", "url": "https://gospeak.io/license"},
            |  "version": "1.0.0"
            |}""".stripMargin)
        val info = Info(
          "My api",
          Some(Markdown("desc")),
          Some(Url("https://gospeak.io/tos")),
          Some(Info.Contact(Some("Support"), Some(Url("https://gospeak.io/support")), Some(Email("contact@gospeak.io")), Option.empty[TODO])),
          Some(Info.License("Apache 2.0", Some(Url("https://gospeak.io/license")), Option.empty[TODO])),
          Version(1),
          Option.empty[TODO])
        json.validate[Info] shouldBe JsSuccess(info)
        Json.toJson(info) shouldBe json
      }
    }
  }
}
