package gospeak.libs.openapi.models

import gospeak.libs.openapi.OpenApiFactory.Formats._
import gospeak.libs.openapi.models.utils.TODO
import play.api.libs.json.{JsSuccess, Json}
import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers

class ComponentsSpec extends AnyFunSpec with Matchers {
  describe("Components") {
    it("should parse and serialize") {
      val json = Json.parse(ExternalDocSpec.jsonStr)
      json.validate[ExternalDoc] shouldBe JsSuccess(ExternalDocSpec.value)
      Json.toJson(ExternalDocSpec.value) shouldBe json
    }
  }
}

object ComponentsSpec {
  val jsonStr: String =
    s"""{
       |  "schemas": {
       |    "User": ${SchemaSpec.jsonStr}
       |  }
       |}""".stripMargin
  val value = Components(
    schemas = Some(Schemas("User" -> SchemaSpec.value)),
    responses = Option.empty[TODO],
    parameters = Option.empty[TODO],
    examples = Option.empty[TODO],
    requestBodies = Option.empty[TODO],
    headers = Option.empty[TODO],
    securitySchemes = Option.empty[TODO],
    links = Option.empty[TODO],
    callbacks = Option.empty[TODO],
    extensions = Option.empty[TODO])
}
