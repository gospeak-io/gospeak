package gospeak.libs.openapi.models

import gospeak.libs.testingutils.BaseSpec

class PathSpec extends BaseSpec {
  describe("Path") {
    it("should extract variables") {
      Path("/api").variables shouldBe List()
      Path("/users/{user}/messages/{message}").variables shouldBe List("user", "message")
    }
    it("should map variables") {
      Path("/users/{user}/messages/{message}").mapVariables(_ => "?") shouldBe Path("/users/?/messages/?")

      val values = Map("user" -> "123", "message" -> "456")
      Path("/users/{user}/messages/{message}").mapVariables(values) shouldBe Path("/users/123/messages/456")
    }
  }
}
