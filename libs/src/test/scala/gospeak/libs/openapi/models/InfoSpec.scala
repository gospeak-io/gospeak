package gospeak.libs.openapi.models

import gospeak.libs.openapi.OpenApiFactory.Formats._
import gospeak.libs.openapi.models.utils._
import gospeak.libs.testingutils.BaseSpec
import play.api.libs.json.{JsSuccess, Json}

class InfoSpec extends BaseSpec {
  describe("Info") {
    it("should parse and serialize") {
      val json = Json.parse(InfoSpec.jsonStr)
      json.validate[Info] shouldBe JsSuccess(InfoSpec.value)
      Json.toJson(InfoSpec.value) shouldBe json
    }
  }
}

object InfoSpec {
  val jsonStr: String =
    """{
      |  "title": "My api",
      |  "description": "desc",
      |  "termsOfService": "https://gospeak.io/tos",
      |  "contact": {"name": "Support", "url": "https://gospeak.io/support", "email": "contact@gospeak.io"},
      |  "license": {"name": "Apache 2.0", "url": "https://gospeak.io/license"},
      |  "version": "1.0.0"
      |}""".stripMargin
  val value: Info = Info(
    title = "My api",
    description = Some(Markdown("desc")),
    termsOfService = Some(Url("https://gospeak.io/tos")),
    contact = Some(Info.Contact(Some("Support"), Some(Url("https://gospeak.io/support")), Some(Email("contact@gospeak.io")), Option.empty[TODO])),
    license = Some(Info.License("Apache 2.0", Some(Url("https://gospeak.io/license")), Option.empty[TODO])),
    version = Version(1),
    extensions = Option.empty[TODO])
}
