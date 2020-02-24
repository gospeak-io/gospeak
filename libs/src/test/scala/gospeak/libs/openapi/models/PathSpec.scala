package gospeak.libs.openapi.models

import org.scalatest.{FunSpec, Matchers}

class PathSpec extends FunSpec with Matchers {
  describe("Path") {
    it("should extract variables") {
      Path("/api").variables shouldBe Seq()
      Path("/users/{user}/messages/{message}").variables shouldBe Seq("user", "message")
    }
    it("should map variables") {
      Path("/users/{user}/messages/{message}").mapVariables(_ => "?") shouldBe Path("/users/?/messages/?")

      val values = Map("user" -> "123", "message" -> "456")
      Path("/users/{user}/messages/{message}").mapVariables(values) shouldBe Path("/users/123/messages/456")
    }
  }
}
