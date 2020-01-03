package fr.gospeak.web.services.openapi

import fr.gospeak.web.services.openapi.error.OpenApiError.{ErrorMessage, ValidationError}
import fr.gospeak.web.services.openapi.error.OpenApiErrors
import fr.gospeak.web.services.openapi.models.utils._
import fr.gospeak.web.services.openapi.models.{Info, OpenApi, Server}
import org.scalatest.{FunSpec, Matchers}
import play.api.libs.json.{JsError, JsSuccess, Json, JsonValidationError}

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
        None,
        Option.empty[TODO],
        Option.empty[TODO],
        Option.empty[TODO],
        Option.empty[TODO],
        Option.empty[TODO],
        Option.empty[TODO])
      OpenApiFactory.parseJson(json) shouldBe Right(openApi)
      OpenApiFactory.toJson(openApi) shouldBe json
    }
    it("should parse a complex OpenApi spec") {
      val json = Json.parse(
        s"""{
           |  "openapi": "3.0.2",
           |  "info": ${OpenApiFactorySpec.infoJson},
           |  "servers": [${OpenApiFactorySpec.serverJson}]
           |}""".stripMargin)
      val openApi = OpenApi(
        Version(3, 0, 2),
        OpenApiFactorySpec.info,
        Some(Seq(OpenApiFactorySpec.server)),
        Option.empty[TODO],
        Option.empty[TODO],
        Option.empty[TODO],
        Option.empty[TODO],
        Option.empty[TODO],
        Option.empty[TODO])
      OpenApiFactory.parseJson(json) shouldBe Right(openApi)
      OpenApiFactory.toJson(openApi) shouldBe json
    }
    it("should return required key errors") {
      OpenApiFactory.parseJson(Json.parse("{}")) shouldBe Left(OpenApiErrors(
        ValidationError(List(".openapi"), List(ErrorMessage.missingPath())),
        ValidationError(List(".info"), List(ErrorMessage.missingPath()))
      ))
    }
    it("should return bad format errors") {
      OpenApiFactory.parseJson(Json.parse(
        """{
          |  "openapi": 1,
          |  "info": "a"
          |}""".stripMargin)) shouldBe Left(OpenApiErrors(
        ValidationError(List(".openapi"), List(ErrorMessage.expectString())),
        ValidationError(List(".info"), List(ErrorMessage.expectObject()))
      ))
    }
    it("should return bad validation errors") {
      OpenApiFactory.parseJson(Json.parse(
        """{
          |  "openapi": "a",
          |  "info": {"title": "t", "version": "b"}
          |}""".stripMargin)) shouldBe Left(OpenApiErrors(
        ValidationError(List(".openapi"), List(ErrorMessage.validationFailed("a", "x.y.z", "Version"))),
        ValidationError(List(".info", ".version"), List(ErrorMessage.validationFailed("b", "x.y.z", "Version")))
      ))
    }
    describe("Info") {
      it("should parse a full featured Info") {
        import OpenApiFactory.Formats._
        val json = Json.parse(OpenApiFactorySpec.infoJson)
        json.validate[Info] shouldBe JsSuccess(OpenApiFactorySpec.info)
        Json.toJson(OpenApiFactorySpec.info) shouldBe json
      }
    }
    describe("Server") {
      it("should parse a full featured Server") {
        import OpenApiFactory.Formats._
        val json = Json.parse(OpenApiFactorySpec.serverJson)
        json.validate[Server] shouldBe JsSuccess(OpenApiFactorySpec.server)
        Json.toJson(OpenApiFactorySpec.server) shouldBe json
      }
      it("should fail when missing a variable") {
        import OpenApiFactory.Formats._
        val json = Json.parse("""{"url": "https://gospeak.io:{port}/api"}""")
        json.validate[Server] shouldBe JsError(JsonValidationError("error.variable.missing", "port"))
      }
    }
  }
}

object OpenApiFactorySpec {
  val infoJson: String =
    """{
      |  "title": "My api",
      |  "description": "desc",
      |  "termsOfService": "https://gospeak.io/tos",
      |  "contact": {"name": "Support", "url": "https://gospeak.io/support", "email": "contact@gospeak.io"},
      |  "license": {"name": "Apache 2.0", "url": "https://gospeak.io/license"},
      |  "version": "1.0.0"
      |}""".stripMargin
  val info = Info(
    "My api",
    Some(Markdown("desc")),
    Some(Url("https://gospeak.io/tos")),
    Some(Info.Contact(Some("Support"), Some(Url("https://gospeak.io/support")), Some(Email("contact@gospeak.io")), Option.empty[TODO])),
    Some(Info.License("Apache 2.0", Some(Url("https://gospeak.io/license")), Option.empty[TODO])),
    Version(1),
    Option.empty[TODO])

  val serverJson: String =
    """{
      |  "url": "https://gospeak.io:{port}/api",
      |  "description": "Prod",
      |  "variables": {
      |    "port": {
      |      "default": "8443",
      |      "enum": ["8443", "443"],
      |      "description": "The port to use"
      |    }
      |  }
      |}""".stripMargin
  val server = Server(
    Url("https://gospeak.io:{port}/api"),
    Some(Markdown("Prod")),
    Some(Map("port" -> Server.Variable("8443", Some(Seq("8443", "443")), Some(Markdown("The port to use"))))),
    Option.empty[TODO])
}
