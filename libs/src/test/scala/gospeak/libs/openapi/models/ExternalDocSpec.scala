package gospeak.libs.openapi.models

import gospeak.libs.openapi.OpenApiFactory.Formats._
import gospeak.libs.openapi.models.utils.{Markdown, TODO, Url}
import gospeak.libs.testingutils.BaseSpec
import play.api.libs.json.{JsSuccess, Json}

class ExternalDocSpec extends BaseSpec {
  describe("ExternalDoc") {
    it("should parse and serialize") {
      val json = Json.parse(ExternalDocSpec.jsonStr)
      json.validate[ExternalDoc] shouldBe JsSuccess(ExternalDocSpec.value)
      Json.toJson(ExternalDocSpec.value) shouldBe json
    }
  }
}

object ExternalDocSpec {
  val jsonStr: String =
    """{
      |  "url": "https://gospeak.io/docs",
      |  "description": "More doc on API"
      |}""".stripMargin
  val value: ExternalDoc = ExternalDoc(
    url = Url("https://gospeak.io/docs"),
    description = Some(Markdown("More doc on API")),
    extensions = Option.empty[TODO])
}
