package fr.gospeak.web.pages.speaker.talks

import fr.gospeak.libs.scalautils.domain.Page
import fr.gospeak.web.testingutils.CtrlSpec
import org.scalatest.BeforeAndAfterEach
import play.api.http.Status
import play.api.test.Helpers._

class TalkCtrlSpec extends CtrlSpec with BeforeAndAfterEach {
  private val params = Page.Params()
  private val ctrl = new TalkCtrl(cc, silhouette, db.user, db.userRequest, db.event, db.talk, db.proposal, emailSrv)

  override def beforeEach(): Unit = db.migrate().unsafeRunSync()

  override def afterEach(): Unit = db.dropTables().unsafeRunSync()

  describe("TalkCtrl") {
    describe("list") {
      it("should return 200") {
        val res = ctrl.list(params).apply(securedReq)
        status(res) shouldBe Status.OK
        contentAsString(res) should include("""<div class="jumbotron">""")
      }
    }
  }
}
