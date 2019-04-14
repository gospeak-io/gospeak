package fr.gospeak.web.pages.published

import fr.gospeak.web.testingutils.TwirlSpec

class IndexSpec extends TwirlSpec {
  describe("index.scala.html") {
    it("should contain title") {
      html.index()(h).toString should include("""<h1 class="home-title">Gospeak</h1>""")
    }
  }
}
