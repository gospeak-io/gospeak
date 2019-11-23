package fr.gospeak.web.pages.published

import fr.gospeak.web.testingutils.CtrlSpec
import play.api.http.Status
import play.api.test.Helpers._

class HomeCtrlSpec extends CtrlSpec {
  private val ctrl = new HomeCtrl(cc, silhouette, conf.application.env)

  describe("HomeCtrl") {
    it("should return 200") {
      val res = ctrl.index().apply(unsecuredReq)
      status(res) shouldBe Status.OK
      contentAsString(res) should include("Introducing Gospeak")
    }
  }
}
