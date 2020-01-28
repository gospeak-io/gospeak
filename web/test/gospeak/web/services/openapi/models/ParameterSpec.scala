package gospeak.web.services.openapi.models

import gospeak.web.services.openapi.OpenApiFactory.Formats._
import gospeak.web.services.openapi.models.utils.Markdown
import org.scalatest.{FunSpec, Matchers}
import play.api.libs.json.{JsSuccess, Json}

class ParameterSpec extends FunSpec with Matchers {
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
