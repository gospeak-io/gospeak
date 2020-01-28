package gospeak.web.services.openapi.models

import gospeak.web.services.openapi.OpenApiFactory.Formats._
import gospeak.web.services.openapi.models.utils.TODO
import org.scalatest.{FunSpec, Matchers}
import play.api.libs.json.{JsSuccess, Json}

class ComponentsSpec extends FunSpec with Matchers {
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
