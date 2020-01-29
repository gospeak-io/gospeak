package gospeak.web.pages.published

import gospeak.web.testingutils.TwirlSpec

class IndexSpec extends TwirlSpec {
  describe("published.index.scala.html") {
    it("should contain title") {
      html.index()(b).toString should include("Introducing Gospeak")
    }
  }
}
