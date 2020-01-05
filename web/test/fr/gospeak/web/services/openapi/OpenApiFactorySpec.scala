package fr.gospeak.web.services.openapi

import cats.data.NonEmptyList
import fr.gospeak.web.services.openapi.error.OpenApiError.{ErrorMessage, ValidationError}
import fr.gospeak.web.services.openapi.error.OpenApiErrors
import fr.gospeak.web.services.openapi.models._
import fr.gospeak.web.services.openapi.models.utils._
import fr.gospeak.web.utils.Extensions._
import org.scalatest.{FunSpec, Matchers}
import play.api.libs.json.{JsError, JsSuccess, Json}

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
        Option.empty[ExternalDoc],
        Option.empty[List[Server]],
        Option.empty[List[Tag]],
        Option.empty[TODO],
        Option.empty[TODO],
        Option.empty[Components],
        Option.empty[TODO])
      OpenApiFactory.parseJson(json) shouldBe Right(openApi)
      OpenApiFactory.toJson(openApi) shouldBe json
    }
    it("should parse a complex OpenApi spec") {
      val json = Json.parse(
        s"""{
           |  "openapi": "3.0.2",
           |  "info": ${OpenApiFactorySpec.infoJson},
           |  "externalDocs": ${OpenApiFactorySpec.externalDocsJson},
           |  "servers": [${OpenApiFactorySpec.serverJson}],
           |  "tags": [${OpenApiFactorySpec.tagJson}],
           |  "components": ${OpenApiFactorySpec.componentsJson}
           |}""".stripMargin)
      val openApi = OpenApi(
        Version(3, 0, 2),
        OpenApiFactorySpec.info,
        Some(OpenApiFactorySpec.externalDocs),
        Some(List(OpenApiFactorySpec.server)),
        Some(List(OpenApiFactorySpec.tag)),
        Option.empty[TODO],
        Option.empty[TODO],
        Some(OpenApiFactorySpec.components),
        Option.empty[TODO])
      OpenApiFactory.parseJson(json) shouldBe Right(openApi)
      OpenApiFactory.toJson(openApi) shouldBe json
    }
    it("should return required key errors") {
      OpenApiFactory.parseJson(Json.parse("{}")) shouldBe Left(OpenApiErrors(
        ValidationError(List(".openapi"), NonEmptyList.of(ErrorMessage.missingPath())),
        ValidationError(List(".info"), NonEmptyList.of(ErrorMessage.missingPath()))
      ))
    }
    it("should return bad format errors") {
      OpenApiFactory.parseJson(Json.parse(
        """{
          |  "openapi": 1,
          |  "info": "a"
          |}""".stripMargin)) shouldBe Left(OpenApiErrors(
        ValidationError(List(".openapi"), NonEmptyList.of(ErrorMessage.expectString())),
        ValidationError(List(".info"), NonEmptyList.of(ErrorMessage.expectObject()))
      ))
    }
    it("should return bad validation errors") {
      OpenApiFactory.parseJson(Json.parse(
        """{
          |  "openapi": "a",
          |  "info": {"title": "t", "version": "b"}
          |}""".stripMargin)) shouldBe Left(OpenApiErrors(
        ValidationError(List(".openapi"), NonEmptyList.of(ErrorMessage.badFormat("a", "Version", "1.2.3"))),
        ValidationError(List(".info", ".version"), NonEmptyList.of(ErrorMessage.badFormat("b", "Version", "1.2.3")))
      ))
    }
    it("should fail on duplicate tag") {
      val json = Json.parse(
        """{
          |  "openapi": "3.0.2",
          |  "info": {
          |    "title": "My api",
          |    "version": "1.0.0"
          |  },
          |  "tags": [
          |    {"name": "aaa"},
          |    {"name": "aaa"}
          |  ]
          |}""".stripMargin)
      OpenApiFactory.parseJson(json) shouldBe Left(OpenApiErrors(
        ValidationError(List(), NonEmptyList.of(ErrorMessage.duplicateValue("aaa", "tags")))
      ))
    }
    it("should fail on missing reference") {
      val json = Json.parse(
        """{
          |  "openapi": "3.0.2",
          |  "info": {
          |    "title": "My api",
          |    "version": "1.0.0"
          |  },
          |  "components": {
          |    "schemas": {
          |      "User": {"$ref": "#/components/schemas/Miss"},
          |      "Test": {"$ref": "#/components/miss/User"}
          |    }
          |  }
          |}""".stripMargin)
      OpenApiFactory.parseJson(json) shouldBe Left(OpenApiErrors(ValidationError(List(), NonEmptyList.of(
        ErrorMessage.missingReference("#/components/schemas/Miss"),
        ErrorMessage.unknownReference("#/components/miss/User", "miss")
      ))))
    }
    describe("Info") {
      import OpenApiFactory.Formats._
      it("should parse a full featured Info") {
        val json = Json.parse(OpenApiFactorySpec.infoJson)
        json.validate[Info] shouldBe JsSuccess(OpenApiFactorySpec.info)
        Json.toJson(OpenApiFactorySpec.info) shouldBe json
      }
    }
    describe("ExternalDoc") {
      import OpenApiFactory.Formats._
      it("should parse a full featured ExternalDoc") {
        val json = Json.parse(OpenApiFactorySpec.externalDocsJson)
        json.validate[ExternalDoc] shouldBe JsSuccess(OpenApiFactorySpec.externalDocs)
        Json.toJson(OpenApiFactorySpec.externalDocs) shouldBe json
      }
    }
    describe("Server") {
      import OpenApiFactory.Formats._
      it("should parse a full featured Server") {
        val json = Json.parse(OpenApiFactorySpec.serverJson)
        json.validate[Server] shouldBe JsSuccess(OpenApiFactorySpec.server)
        Json.toJson(OpenApiFactorySpec.server) shouldBe json
      }
      it("should fail when missing a variable") {
        val json = Json.parse("""{"url": "https://gospeak.io:{port}/api"}""")
        json.validate[Server] shouldBe JsError(ErrorMessage.missingVariable("port").toJson)
      }
    }
    describe("Components") {
      import OpenApiFactory.Formats._
      it("should parse a full featured Components") {
        val json = Json.parse(OpenApiFactorySpec.componentsJson)
        json.validate[Components] shouldBe JsSuccess(OpenApiFactorySpec.components)
        Json.toJson(OpenApiFactorySpec.components) shouldBe json
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

  val externalDocsJson: String =
    """{
      |  "url": "https://gospeak.io/docs",
      |  "description": "More doc on API"
      |}""".stripMargin
  val externalDocs = ExternalDoc(Url("https://gospeak.io/docs"), Some(Markdown("More doc on API")), Option.empty[TODO])

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
    Some(Map("port" -> Server.Variable("8443", Some(List("8443", "443")), Some(Markdown("The port to use")), Option.empty[TODO]))),
    Option.empty[TODO])

  val tagJson: String =
    """{
      |  "name": "public",
      |  "description": "Public API",
      |  "externalDocs": {
      |    "url": "https://gospeak.io/tags/docs",
      |    "description": "More details"
      |  }
      |}""".stripMargin
  val tag = Tag(
    "public",
    Some(Markdown("Public API")),
    Some(ExternalDoc(Url("https://gospeak.io/tags/docs"), Some(Markdown("More details")), Option.empty[TODO])),
    Option.empty[TODO])

  val componentsJson: String =
    """{
      |  "schemas": {
      |    "User": {
      |      "type": "object",
      |      "properties": {
      |        "id": {"type": "integer", "format": "int64", "example": 1, "default": 1, "description": "An id", "minimum": 0},
      |        "name": {"type": "string", "format": "username", "example": "lkn", "description": "User name"},
      |        "flags": {"type": "array", "items": {"type": "boolean"}, "example": [true, false], "description": "feature flags"},
      |        "createdAt": {"$ref": "#/components/schemas/Instant"}
      |      },
      |      "description": "A user",
      |      "required": ["id", "name"]
      |    },
      |    "Instant": {"type": "string", "format": "date-time"}
      |  }
      |}""".stripMargin
  val components = Components(
    Some(Map(
      "User" -> Schema.ObjectVal(Map(
        "id" -> Schema.IntegerVal(Some("int64"), Some(1), Some(1), Some(Markdown("An id")), Some(0)),
        "name" -> Schema.StringVal(Some("username"), Some("lkn"), None, Some(Markdown("User name"))),
        "flags" -> Schema.ArrayVal(Schema.BooleanVal(None, None, None), Some(List(true, false).map(Js(_))), Some(Markdown("feature flags"))),
        "createdAt" -> Schema.ReferenceVal(Reference.schema("Instant"))
      ), Some(Markdown("A user")), Some(List("id", "name"))),
      "Instant" -> Schema.StringVal(Some("date-time"), None, None, None))),
    Option.empty[TODO],
    Option.empty[TODO],
    Option.empty[TODO],
    Option.empty[TODO],
    Option.empty[TODO],
    Option.empty[TODO],
    Option.empty[TODO],
    Option.empty[TODO],
    Option.empty[TODO])
}
