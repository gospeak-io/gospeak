package gospeak.web.pages.published

import gospeak.web.testingutils.CtrlSpec
import play.api.http.Status
import play.api.test.Helpers._

class HomeCtrlSpec extends CtrlSpec {
  private val ctrl = new HomeCtrl(cc, silhouette, conf)

  describe("HomeCtrl") {
    it("should return 200") {
      val res = ctrl.index().apply(unsecuredReq)
      status(res) shouldBe Status.OK
      contentAsString(res) should include("Introducing Gospeak")
    }
  }
}
