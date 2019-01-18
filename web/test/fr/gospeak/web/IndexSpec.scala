package fr.gospeak.web

import fr.gospeak.web.testingutils.Values._
import org.scalatest.{FunSpec, Matchers}

class IndexSpec extends FunSpec with Matchers {
  describe("index.scala.html") {
    it("should contain title") {
      html.index()(h)(None).toString should include("""<h1 class="home-title">Gospeak</h1>""")
    }
  }
}
