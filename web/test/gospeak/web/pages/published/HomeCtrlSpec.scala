package gospeak.web.pages.published

import gospeak.web.testingutils.CtrlSpec
import play.api.http.Status
import play.api.test.Helpers._

class HomeCtrlSpec extends CtrlSpec {
  private val ctrl = new HomeCtrl(cc, silhouette, conf, db.user, db.talk, db.group, db.cfp, db.event, db.proposal, db.externalCfp, db.externalEvent, db.externalProposal)

  describe("HomeCtrl") {
    it("should return 200") {
      val res = ctrl.index().apply(unsecuredReq)
      status(res) shouldBe Status.OK
      contentAsString(res) should include("Introducing Gospeak")
    }
  }
}
