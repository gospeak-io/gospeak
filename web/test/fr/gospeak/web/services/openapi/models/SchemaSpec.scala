package fr.gospeak.web.services.openapi.models

import fr.gospeak.web.services.openapi.OpenApiFactory.Formats._
import fr.gospeak.web.services.openapi.error.OpenApiError
import fr.gospeak.web.services.openapi.models.utils.{Js, Markdown}
import fr.gospeak.web.services.openapi.JsonUtils._
import org.scalatest.{FunSpec, Matchers}
import play.api.libs.json.{JsError, JsSuccess, Json}

class SchemaSpec extends FunSpec with Matchers {
  describe("Schema") {
    it("should parse and serialize") {
      val json = Json.parse(HeaderSpec.jsonStr)
      json.validate[Header] shouldBe JsSuccess(HeaderSpec.value)
      Json.toJson(HeaderSpec.value) shouldBe json
    }
    it("should parse a StringVal") {
      val json = Json.parse("""{"type": "string", "format": "date", "example": "2020-01-04T12:15:19.212Z", "description": "creation date"}""")
      val schema = Schema.StringVal(Some("date"), None, Some("2020-01-04T12:15:19.212Z"), None, Some(Markdown("creation date")))
      json.validate[Schema.StringVal] shouldBe JsSuccess(schema)
      Json.toJson(schema) shouldBe json
      json.validate[Schema] shouldBe JsSuccess(schema)
      Json.toJson(schema: Schema) shouldBe json

      val badJson = Json.parse("""{"type": "number"}""")
      badJson.validate[Schema.StringVal] shouldBe JsError(Seq(OpenApiError.badHintValue("number", "string", "type").toJson))
    }
    it("should parse a IntegerVal") {
      val json = Json.parse("""{"type": "integer", "format": "int32", "example": 12, "default": 1, "description": "page no", "minimum": 1}""")
      val schema = Schema.IntegerVal(Some("int32"), None, Some(12), Some(1), Some(Markdown("page no")), Some(1))
      json.validate[Schema.IntegerVal] shouldBe JsSuccess(schema)
      Json.toJson(schema) shouldBe json
      json.validate[Schema] shouldBe JsSuccess(schema)
      Json.toJson(schema: Schema) shouldBe json

      val badJson = Json.parse("""{"type": "string"}""")
      badJson.validate[Schema.IntegerVal] shouldBe JsError(Seq(OpenApiError.badHintValue("string", "integer", "type").toJson))
    }
    it("should parse a NumberVal") {
      val json = Json.parse("""{"type": "number", "format": "double", "example": 4.5, "default": 0.1, "description": "a rating"}""")
      val schema = Schema.NumberVal(Some("double"), None, Some(4.5), Some(0.1), Some(Markdown("a rating")))
      json.validate[Schema.NumberVal] shouldBe JsSuccess(schema)
      Json.toJson(schema) shouldBe json
      json.validate[Schema] shouldBe JsSuccess(schema)
      Json.toJson(schema: Schema) shouldBe json

      val badJson = Json.parse("""{"type": "string"}""")
      badJson.validate[Schema.NumberVal] shouldBe JsError(Seq(OpenApiError.badHintValue("string", "number", "type").toJson))
    }
    it("should parse a BooleanVal") {
      val json = Json.parse("""{"type": "boolean", "example": true, "default": false, "description": "is admin"}""")
      val schema = Schema.BooleanVal(Some(true), Some(false), Some(Markdown("is admin")))
      json.validate[Schema.BooleanVal] shouldBe JsSuccess(schema)
      Json.toJson(schema) shouldBe json
      json.validate[Schema] shouldBe JsSuccess(schema)
      Json.toJson(schema: Schema) shouldBe json

      val badJson = Json.parse("""{"type": "string"}""")
      badJson.validate[Schema.BooleanVal] shouldBe JsError(Seq(OpenApiError.badHintValue("string", "boolean", "type").toJson))
    }
    it("should parse a ArrayVal") {
      val json = Json.parse("""{"type": "array", "items": {"type": "string"}, "example": ["tag"], "description": "list of tags"}""")
      val schema = Schema.ArrayVal(Schema.StringVal(None, None, None, None, None), Some(List(Js("tag"))), Some(Markdown("list of tags")))
      json.validate[Schema.ArrayVal] shouldBe JsSuccess(schema)
      Json.toJson(schema) shouldBe json
      json.validate[Schema] shouldBe JsSuccess(schema)
      Json.toJson(schema: Schema) shouldBe json

      val badJson = Json.parse("""{"type": "string"}""")
      badJson.validate[Schema.ArrayVal] shouldBe JsError(Seq(OpenApiError.badHintValue("string", "array", "type").toJson))
    }
    it("should parse a ObjectVal") {
      val json = Json.parse("""{"type": "object", "properties": {"id": {"type": "string"}}, "description": "a user", "required": ["id"]}""")
      val schema = Schema.ObjectVal(Map("id" -> Schema.StringVal(None, None, None, None, None)), Some(Markdown("a user")), Some(List("id")))
      json.validate[Schema.ObjectVal] shouldBe JsSuccess(schema)
      Json.toJson(schema) shouldBe json
      json.validate[Schema] shouldBe JsSuccess(schema)
      Json.toJson(schema: Schema) shouldBe json

      val badJson = Json.parse("""{"type": "string"}""")
      badJson.validate[Schema.ObjectVal] shouldBe JsError(Seq(OpenApiError.badHintValue("string", "object", "type").toJson))
    }
    it("should parse a ReferenceVal") {
      val json = Json.parse("""{"$ref": "#/components/schemas/Instant"}""")
      val schema = Schema.ReferenceVal(Reference.schema("Instant"))
      json.validate[Schema.ReferenceVal] shouldBe JsSuccess(schema)
      Json.toJson(schema) shouldBe json
      json.validate[Schema] shouldBe JsSuccess(schema)
      Json.toJson(schema: Schema) shouldBe json
    }
    it("should parse a CombinationVal") {
      val json = Json.parse("""{"oneOf": [{"$ref": "#/components/schemas/Instant"}, {"type": "boolean"}]}""")
      val schema = Schema.CombinationVal(Some(List(Schema.ReferenceVal(Reference.schema("Instant")), Schema.BooleanVal(None, None, None))))
      json.validate[Schema.CombinationVal] shouldBe JsSuccess(schema)
      Json.toJson(schema) shouldBe json
      json.validate[Schema] shouldBe JsSuccess(schema)
      Json.toJson(schema: Schema) shouldBe json
    }
    describe("flatten") {
      it("should return the list of nested Schemas with the current one") {
        val strVal = Schema.StringVal(None, None, None, None, None)
        val intVal = Schema.IntegerVal(None, None, None, None, None, None)
        val numVal = Schema.NumberVal(None, None, None, None, None)
        val boolVal = Schema.BooleanVal(None, None, None)
        val refVal = Schema.ReferenceVal(Reference.schema("Id"))
        val arrStrVal = Schema.ArrayVal(strVal, None, None)
        val emptyObjVal = Schema.ObjectVal(Map(), None, None)
        val objStrVal = Schema.ObjectVal(Map("id" -> strVal), None, None)
        val arrObjVal = Schema.ArrayVal(objStrVal, None, None)
        val objArrObjVal = Schema.ObjectVal(Map("id" -> strVal, "tags" -> arrObjVal, "count" -> intVal), None, None)

        strVal.flatten shouldBe List(strVal)
        intVal.flatten shouldBe List(intVal)
        numVal.flatten shouldBe List(numVal)
        boolVal.flatten shouldBe List(boolVal)
        refVal.flatten shouldBe List(refVal)
        arrStrVal.flatten shouldBe List(arrStrVal, strVal)
        emptyObjVal.flatten shouldBe List(emptyObjVal)
        objStrVal.flatten shouldBe List(objStrVal, strVal)
        arrObjVal.flatten shouldBe List(arrObjVal, objStrVal, strVal)
        objArrObjVal.flatten shouldBe List(objArrObjVal, strVal, arrObjVal, objStrVal, strVal, intVal)
      }
    }
  }
}

object SchemaSpec {
  val jsonStr: String =
    """{
      |  "type": "object",
      |  "properties": {
      |    "id": {"type": "integer", "format": "int64", "example": 1, "default": 1, "description": "An id", "minimum": 0},
      |    "name": {"type": "string", "format": "username", "example": "lkn", "description": "User name"},
      |    "flags": {"type": "array", "items": {"type": "boolean"}, "example": [true, false], "description": "feature flags"}
      |  },
      |  "description": "A user",
      |  "required": ["id", "name"]
      |}""".stripMargin
  val value = Schema.ObjectVal(Map(
    "id" -> Schema.IntegerVal(Some("int64"), None, Some(1), Some(1), Some(Markdown("An id")), Some(0)),
    "name" -> Schema.StringVal(Some("username"), None, Some("lkn"), None, Some(Markdown("User name"))),
    "flags" -> Schema.ArrayVal(Schema.BooleanVal(None, None, None), Some(List(true, false).map(Js(_))), Some(Markdown("feature flags")))
  ), Some(Markdown("A user")), Some(List("id", "name")))
}
