package gospeak.web.pages.published.cfps

import gospeak.core.domain.messages.Message
import gospeak.libs.scala.BasicMessageBus
import gospeak.libs.scala.domain.Page
import gospeak.web.services.MessageSrv
import gospeak.web.testingutils.CtrlSpec
import org.scalatest.BeforeAndAfterEach
import play.api.http.Status
import play.api.test.Helpers._

class CfpCtrlSpec extends CtrlSpec with BeforeAndAfterEach {
  private val params = Page.Params()
  private val messageSrv = new MessageSrv(db.group, db.cfp, db.venue, db.proposal, db.sponsor, db.user)
  private val messageBus = new BasicMessageBus[Message]()
  private val ctrl = new CfpCtrl(cc, silhouette, conf, db.group, db.cfp, db.talk, db.proposal, db.userRequest, db.externalEvent, db.externalCfp, authSrv, emailSrv, messageSrv, messageBus)

  override def beforeEach(): Unit = db.migrate().unsafeRunSync()

  override def afterEach(): Unit = db.dropTables().unsafeRunSync()

  describe("CfpCtrl") {
    it("should display the cfp list page empty") {
      val res = ctrl.list(params).apply(unsecuredReq)
      status(res) shouldBe Status.OK
    }
  }
}
