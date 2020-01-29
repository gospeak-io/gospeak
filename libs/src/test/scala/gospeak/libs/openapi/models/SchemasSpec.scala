package gospeak.libs.openapi.models

import gospeak.libs.openapi.models.utils.Markdown
import org.scalatest.{FunSpec, Matchers}

class SchemasSpec extends FunSpec with Matchers {
  private val schemas = Schemas(
    "Str" -> Schema.StringVal(Some("str"), None, None, None, None),
    "Int" -> Schema.IntegerVal(None, None, None, None, Some(Markdown("int")), None),
    "Arr" -> Schema.ArrayVal(Schema.ReferenceVal(Reference.schema("Int")), None, Some(Markdown("arr"))))

  describe("Schemas") {
    describe("resolve") {
      it("should resolve simple reference") {
        schemas.resolve(
          Schema.ReferenceVal(Reference.schema("Str"))
        ) shouldBe Right(Some(Schema.StringVal(Some("str"), None, None, None, None)))
      }
      it("should resolve reference in array") {
        schemas.resolve(
          Schema.ArrayVal(Schema.ReferenceVal(Reference.schema("Int")), None, Some(Markdown("arr1")))
        ) shouldBe Right(Some(Schema.ArrayVal(Schema.IntegerVal(None, None, None, None, Some(Markdown("int")), None), None, Some(Markdown("arr1")))))
      }
      it("should resolve references in object") {
        schemas.resolve(Schema.ObjectVal(Map(
          "name" -> Schema.StringVal(None, None, None, None, None),
          "tags" -> Schema.ReferenceVal(Reference.schema("Arr"))
        ), Some(Markdown("obj")), None)) shouldBe Right(Some(Schema.ObjectVal(Map(
          "name" -> Schema.StringVal(None, None, None, None, None),
          "tags" -> Schema.ArrayVal(Schema.IntegerVal(None, None, None, None, Some(Markdown("int")), None), None, Some(Markdown("arr")))
        ), Some(Markdown("obj")), None)))
      }
    }
  }
}
