package gospeak.libs.openapi.models

import gospeak.libs.openapi.OpenApiFactory.Formats._
import gospeak.libs.openapi.models.utils.{Markdown, TODO}
import gospeak.libs.testingutils.BaseSpec
import play.api.libs.json.{JsSuccess, Json}

class LinkSpec extends BaseSpec {
  describe("Link") {
    it("should parse and serialize") {
      val json = Json.parse(LinkSpec.jsonStr)
      json.validate[Link] shouldBe JsSuccess(LinkSpec.value)
      Json.toJson(LinkSpec.value) shouldBe json
    }
  }
}

object LinkSpec {
  val jsonStr: String =
    s"""{
       |  "operationId": "id",
       |  "description": "desc",
       |  "server": ${ServerSpec.jsonStr}
       |}""".stripMargin
  val value: Link = Link(
    operationId = Some("id"),
    operationRef = None,
    description = Some(Markdown("desc")),
    parameters = Option.empty[Map[String, TODO]],
    requestBody = Option.empty[TODO],
    server = Some(ServerSpec.value))
}
