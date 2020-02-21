package gospeak.libs.openapi.models

import gospeak.libs.openapi.OpenApiFactory.Formats._
import gospeak.libs.openapi.models.utils.{Markdown, TODO}
import play.api.libs.json.{JsSuccess, Json}
import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers

class LinkSpec extends AnyFunSpec with Matchers {
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
