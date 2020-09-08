package gospeak.libs.openapi.models

import gospeak.libs.openapi.OpenApiFactory.Formats._
import gospeak.libs.openapi.error.OpenApiError
import gospeak.libs.openapi.models.utils.{Markdown, TODO, Url}
import gospeak.libs.testingutils.BaseSpec
import play.api.libs.json.{JsSuccess, Json}

class ServerSpec extends BaseSpec {
  describe("Server") {
    it("should parse and serialize") {
      val json = Json.parse(ServerSpec.jsonStr)
      json.validate[Server] shouldBe JsSuccess(ServerSpec.value)
      Json.toJson(ServerSpec.value) shouldBe json
    }
    it("should extract variables from an Url") {
      Server.extractVariables(Url("https://gospeak.io/api")) shouldBe List()
      Server.extractVariables(Url("https://{user}:{pass}@gospeak.io:{port}/api")) shouldBe List("user", "pass", "port")
    }
    it("should check if all variables are referenced") {
      val s = Schemas()
      Server(Url("https://gospeak.io/api"), None, None, None).getErrors(s) shouldBe List()
      Server(Url("https://gospeak.io/api"), None, Some(Map("port" -> Server.Variable("9000", None, None, None))), None).getErrors(s) shouldBe List()
      Server(Url("https://gospeak.io:{port}/api"), None, Some(Map("port" -> Server.Variable("9000", None, None, None))), None).getErrors(s) shouldBe List()
      Server(Url("https://gospeak.io:{port}/api"), None, None, None).getErrors(s) shouldBe List(OpenApiError.missingVariable("port").atPath(".url"))
    }
    it("should replace variables") {
      Server(Url("https://gospeak.io/api"), None, None, None).expandedUrl shouldBe Url("https://gospeak.io/api")
      Server(Url("https://gospeak.io:{port}/api"), None, Some(Map("port" -> Server.Variable("9000", None, None, None))), None).expandedUrl shouldBe Url("https://gospeak.io:9000/api")
    }
  }
}

object ServerSpec {
  val jsonStr: String =
    """{
      |  "url": "https://gospeak.io:{port}/api",
      |  "description": "Prod",
      |  "variables": {
      |    "port": {
      |      "default": "8443",
      |      "enum": ["8443", "443"],
      |      "description": "The port to use"
      |    }
      |  }
      |}""".stripMargin
  val value: Server = Server(
    Url("https://gospeak.io:{port}/api"),
    Some(Markdown("Prod")),
    Some(Map("port" -> Server.Variable("8443", Some(List("8443", "443")), Some(Markdown("The port to use")), Option.empty[TODO]))),
    Option.empty[TODO])
}
