package fr.gospeak.web

import fr.gospeak.web.testingutils.TwirlSpec
import fr.gospeak.web.testingutils.Values._

class IndexSpec extends TwirlSpec {
  describe("index.scala.html") {
    it("should contain title") {
      html.index()(h).toString should include("""<h1 class="home-title">Gospeak</h1>""")
    }
  }
}
