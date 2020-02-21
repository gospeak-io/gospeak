package gospeak.libs.openapi.models

import gospeak.libs.openapi.OpenApiFactory.Formats._
import gospeak.libs.openapi.models.utils.Markdown
import play.api.libs.json.{JsSuccess, Json}
import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers

class ParameterSpec extends AnyFunSpec with Matchers {
  describe("Parameter") {
    it("should parse and serialize") {
      val json = Json.parse(ParameterSpec.jsonStr)
      json.validate[Parameter] shouldBe JsSuccess(ParameterSpec.value)
      Json.toJson(ParameterSpec.value) shouldBe json
    }
  }
}

object ParameterSpec {
  val jsonStr: String =
    s"""{
       |  "name": "id",
       |  "in": "path",
       |  "description": "an id",
       |  "required": true,
       |  "schema": ${SchemaSpec.jsonStr}
       |}""".stripMargin
  val value = Parameter(
    name = "id",
    in = Parameter.Location.Path,
    deprecated = None,
    description = Some(Markdown("an id")),
    required = Some(true),
    allowEmptyValue = None,
    style = None,
    explode = None,
    allowReserved = None,
    schema = Some(SchemaSpec.value),
    example = None)
}
