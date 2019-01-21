package fr.gospeak.web

import fr.gospeak.web.testingutils.Values._
import org.scalatest.{FunSpec, Matchers}
import play.api.http.Status
import play.api.test.FakeRequest
import play.api.test.Helpers._

class HomeCtrlSpec extends FunSpec with Matchers {
  private val ctrl = new HomeCtrl(cc, db)

  describe("HomeCtrl") {
    it("should return 200") {
      val res = ctrl.index().apply(FakeRequest())
      status(res) shouldBe Status.OK
      contentAsString(res) should include("""<h1 class="home-title">Gospeak</h1>""")
    }
  }
}
