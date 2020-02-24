package gospeak.libs.openapi.models

import gospeak.libs.openapi.OpenApiFactory.Formats._
import gospeak.libs.openapi.models.utils.{Markdown, TODO}
import org.scalatest.{FunSpec, Matchers}
import play.api.libs.json.{JsSuccess, Json}

class TagSpec extends FunSpec with Matchers {
  describe("Tag") {
    it("should parse and serialize") {
      val json = Json.parse(TagSpec.jsonStr)
      json.validate[Tag] shouldBe JsSuccess(TagSpec.value)
      Json.toJson(TagSpec.value) shouldBe json
    }
  }
}

object TagSpec {
  val jsonStr: String =
    s"""{
      |  "name": "public",
      |  "description": "Public API",
      |  "externalDocs": ${ExternalDocSpec.jsonStr}
      |}""".stripMargin
  val value = Tag(
    name = "public",
    description = Some(Markdown("Public API")),
    externalDocs = Some(ExternalDocSpec.value),
    extensions = Option.empty[TODO])
}
