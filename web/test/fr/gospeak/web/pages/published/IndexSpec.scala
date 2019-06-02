package fr.gospeak.web.pages.published

import fr.gospeak.web.testingutils.TwirlSpec

class IndexSpec extends TwirlSpec {
  describe("published.index.scala.html") {
    it("should contain title") {
      html.index()(b).toString should include("""<h1 class="home-title">Gospeak</h1>""")
    }
  }
}
