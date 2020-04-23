package gospeak.web.utils

import gospeak.web.testingutils.BaseSpec

class RoutesUtilsSpec extends BaseSpec {
  describe("RoutesUtils") {
    it("should parse simple routes") {
      RoutesUtils.parseRoute(1, "GET     /       gospeak.HomeCtrl.index") shouldBe Right(Route(1, "GET", "/", "gospeak.HomeCtrl.index"))
      RoutesUtils.parseRoute(2, "GET     /why    gospeak.HomeCtrl.why") shouldBe Right(Route(2, "GET", "/why", "gospeak.HomeCtrl.why"))
    }
    it("should extract variables") {
      Route(1, "GET", "/users/:user/messages/:message", "").variables shouldBe Seq("user", "message")
      Route(1, "GET", "/users/:user/assets/*file", "").variables shouldBe Seq("user", "file")
      Route(1, "GET", "/users/$id<[0-9]+>/messages/:message", "").variables shouldBe Seq("id", "message")
    }
    it("should map variables") {
      Route(1, "GET", "/users/:user/messages/:message", "").mapVariables(_ => "?").path shouldBe "/users/?/messages/?"
      Route(1, "GET", "/users/:user/assets/*file", "").mapVariables(_ => "?").path shouldBe "/users/?/assets/?"
      Route(1, "GET", "/users/$id<[0-9]+>/messages/:message", "").mapVariables(_ => "?").path shouldBe "/users/?/messages/?"

      val values = Map("user" -> "123", "message" -> "456")
      Route(1, "GET", "/users/:user/messages/:message", "").mapVariables(values) shouldBe Route(1, "GET", "/users/123/messages/456", "")
    }
  }
}
