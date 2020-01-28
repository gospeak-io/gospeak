package fr.gospeak.web.services.openapi.models

import fr.gospeak.web.services.openapi.OpenApiFactory.Formats._
import fr.gospeak.web.services.openapi.models.utils.{Markdown, TODO}
import org.scalatest.{FunSpec, Matchers}
import play.api.libs.json.{JsSuccess, Json}

class ResponseSpec extends FunSpec with Matchers {
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
  val value = Response(
    description = Markdown("desc"),
    headers = Some(Map("custom" -> HeaderSpec.value)),
    content = Some(Map("application/json" -> MediaTypeSpec.value)),
    links = Some(Map("ref" -> LinkSpec.value)),
    extensions = Option.empty[TODO])
}
