package gospeak.web.services.openapi.models

import gospeak.web.services.openapi.OpenApiFactory.Formats._
import gospeak.web.services.openapi.models.utils.{Markdown, TODO}
import org.scalatest.{FunSpec, Matchers}
import play.api.libs.json.{JsSuccess, Json}

class LinkSpec extends FunSpec with Matchers {
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
  val value = Link(
    operationId = Some("id"),
    operationRef = None,
    description = Some(Markdown("desc")),
    parameters = Option.empty[Map[String, TODO]],
    requestBody = Option.empty[TODO],
    server = Some(ServerSpec.value))
}
