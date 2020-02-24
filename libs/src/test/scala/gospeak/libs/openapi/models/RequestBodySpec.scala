package gospeak.libs.openapi.models

import gospeak.libs.openapi.OpenApiFactory.Formats._
import gospeak.libs.openapi.models.utils.{Markdown, TODO}
import org.scalatest.{FunSpec, Matchers}
import play.api.libs.json.{JsSuccess, Json}

class RequestBodySpec extends FunSpec with Matchers {
  describe("RequestBody") {
    it("should parse and serialize") {
      val json = Json.parse(RequestBodySpec.jsonStr)
      json.validate[RequestBody] shouldBe JsSuccess(RequestBodySpec.value)
      Json.toJson(RequestBodySpec.value) shouldBe json
    }
  }
}

object RequestBodySpec {
  val jsonStr: String =
    """{
      |  "description": "desc",
      |  "content": {}
      |}""".stripMargin
  val value = RequestBody(
    description = Some(Markdown("desc")),
    content = Map(),
    required = None,
    extensions = Option.empty[TODO])
}
