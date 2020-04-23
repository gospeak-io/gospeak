package gospeak.libs.openapi.models

import gospeak.libs.openapi.OpenApiFactory.Formats._
import gospeak.libs.openapi.models.utils.{Markdown, TODO}
import gospeak.libs.testingutils.BaseSpec
import play.api.libs.json.{JsSuccess, Json}

class ResponseSpec extends BaseSpec {
  describe("Response") {
    it("should parse and serialize") {
      val json = Json.parse(ResponseSpec.jsonStr)
      json.validate[Response] shouldBe JsSuccess(ResponseSpec.value)
      Json.toJson(ResponseSpec.value) shouldBe json
    }
  }
}

object ResponseSpec {
  val jsonStr: String =
    s"""{
       |  "description": "desc",
       |  "headers": {
       |    "custom": ${HeaderSpec.jsonStr}
       |  },
       |  "content": {
       |    "application/json": ${MediaTypeSpec.jsonStr}
       |  },
       |  "links": {
       |    "ref": ${LinkSpec.jsonStr}
       |  }
       |}""".stripMargin
  val value: Response = Response(
    description = Markdown("desc"),
    headers = Some(Map("custom" -> HeaderSpec.value)),
    content = Some(Map("application/json" -> MediaTypeSpec.value)),
    links = Some(Map("ref" -> LinkSpec.value)),
    extensions = Option.empty[TODO])
}
