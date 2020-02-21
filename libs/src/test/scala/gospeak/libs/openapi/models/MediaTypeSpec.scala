package gospeak.libs.openapi.models

import gospeak.libs.openapi.OpenApiFactory.Formats._
import gospeak.libs.openapi.models.utils.TODO
import play.api.libs.json.{JsSuccess, Json}
import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers

class MediaTypeSpec extends AnyFunSpec with Matchers {
  describe("MediaType") {
    it("should parse and serialize") {
      val json = Json.parse(MediaTypeSpec.jsonStr)
      json.validate[MediaType] shouldBe JsSuccess(MediaTypeSpec.value)
      Json.toJson(MediaTypeSpec.value) shouldBe json
    }
  }
}

object MediaTypeSpec {
  val jsonStr: String =
    s"""{
       |  "schema": ${SchemaSpec.jsonStr}
       |}""".stripMargin
  val value = MediaType(
    schema = Some(SchemaSpec.value),
    example = None,
    examples = Option.empty[Map[String, TODO]],
    encoding = Option.empty[TODO],
    extensions = Option.empty[TODO])
}
