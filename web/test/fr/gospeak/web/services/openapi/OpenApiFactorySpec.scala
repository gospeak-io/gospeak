package fr.gospeak.web.services.openapi

import cats.data.NonEmptyList
import fr.gospeak.web.services.openapi.error.OpenApiError.{ErrorMessage, ValidationError}
import fr.gospeak.web.services.openapi.error.OpenApiErrors
import fr.gospeak.web.services.openapi.models._
import fr.gospeak.web.services.openapi.models.utils._
import org.scalatest.{FunSpec, Matchers}
import play.api.libs.json.Json

class OpenApiFactorySpec extends FunSpec with Matchers {
  describe("OpenApiFactory") {
    it("should parse a simple OpenApi spec") {
      val json = Json.parse(
        """{
          |  "openapi": "3.0.2",
          |  "info": {
          |    "title": "My api",
          |    "version": "1.0.0"
          |  },
          |  "paths": {}
          |}""".stripMargin)
      val openApi = OpenApi(
        openapi = Version(3, 0, 2),
        info = Info("My api", None, None, None, None, Version(1), Option.empty[TODO]),
        externalDocs = Option.empty[ExternalDoc],
        servers = Option.empty[List[Server]],
        tags = Option.empty[List[Tag]],
        security = Option.empty[TODO],
        components = Option.empty[Components],
        paths = Map.empty[Path, PathItem],
        extensions = Option.empty[TODO])
      OpenApiFactory.parseJson(json) shouldBe Right(openApi)
      OpenApiFactory.toJson(openApi) shouldBe json
    }
    it("should parse a complex OpenApi spec") {
      val json = Json.parse(
        s"""{
           |  "openapi": "3.0.2",
           |  "info": ${InfoSpec.jsonStr},
           |  "externalDocs": ${ExternalDocSpec.jsonStr},
           |  "servers": [${ServerSpec.jsonStr}],
           |  "tags": [${TagSpec.jsonStr}],
           |  "components": ${ComponentsSpec.jsonStr},
           |  "paths": {
           |    "/api": ${PathItemSpec.jsonStr}
           |  }
           |}""".stripMargin)
      val openApi = OpenApi(
        openapi = Version(3, 0, 2),
        info = InfoSpec.value,
        externalDocs = Some(ExternalDocSpec.value),
        servers = Some(List(ServerSpec.value)),
        tags = Some(List(TagSpec.value)),
        security = Option.empty[TODO],
        components = Some(ComponentsSpec.value),
        paths = Map(Path("/api") -> PathItemSpec.value),
        extensions = Option.empty[TODO])
      OpenApiFactory.parseJson(json) shouldBe Right(openApi)
      OpenApiFactory.toJson(openApi) shouldBe json
    }
    it("should return required key errors") {
      OpenApiFactory.parseJson(Json.parse("{}")) shouldBe Left(OpenApiErrors(
        ValidationError(List(".openapi"), NonEmptyList.of(ErrorMessage.missingPath())),
        ValidationError(List(".paths"), NonEmptyList.of(ErrorMessage.missingPath())),
        ValidationError(List(".info"), NonEmptyList.of(ErrorMessage.missingPath()))
      ))
    }
    it("should return bad format errors") {
      OpenApiFactory.parseJson(Json.parse(
        """{
          |  "openapi": 1,
          |  "info": "a",
          |  "paths": 2
          |}""".stripMargin)) shouldBe Left(OpenApiErrors(
        ValidationError(List(".openapi"), NonEmptyList.of(ErrorMessage.expectString())),
        ValidationError(List(".paths"), NonEmptyList.of(ErrorMessage.expectObject())),
        ValidationError(List(".info"), NonEmptyList.of(ErrorMessage.expectObject()))
      ))
    }
    it("should return bad validation errors") {
      OpenApiFactory.parseJson(Json.parse(
        """{
          |  "openapi": "a",
          |  "info": {"title": "t", "version": "b"},
          |  "paths": {}
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
          |  ],
          |  "paths": {}
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
          |      "User": {"$ref": "#/components/schemas/Miss"}
          |    }
          |  },
          |  "paths": {}
          |}""".stripMargin)
      OpenApiFactory.parseJson(json) shouldBe Left(OpenApiErrors(ValidationError(List(), NonEmptyList.of(
        ErrorMessage.missingReference("#/components/schemas/Miss")
      ))))
    }
  }
}
