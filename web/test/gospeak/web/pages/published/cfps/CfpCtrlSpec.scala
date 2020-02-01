package gospeak.web.pages.published.cfps

import gospeak.core.domain.utils.GsMessage
import gospeak.web.domain.{GsMessageBus, MessageBuilder}
import gospeak.web.testingutils.CtrlSpec
import gospeak.libs.scala.BasicMessageBus
import gospeak.libs.scala.domain.Page
import org.scalatest.BeforeAndAfterEach
import play.api.http.Status
import play.api.test.Helpers._

class CfpCtrlSpec extends CtrlSpec with BeforeAndAfterEach {
  private val params = Page.Params()
  private val messageBuilder = new MessageBuilder()
  private val messageBus = new BasicMessageBus[GsMessage]()
  private val orgaMessageBus = new GsMessageBus(messageBus, messageBuilder)

  private val ctrl = new CfpCtrl(cc, silhouette, conf, db.group, db.cfp, db.talk, db.proposal, db.userRequest, db.externalCfp, authSrv, emailSrv, orgaMessageBus)

  override def beforeEach(): Unit = db.migrate().unsafeRunSync()

  override def afterEach(): Unit = db.dropTables().unsafeRunSync()

  describe("CfpCtrl") {
    it("should display the cfp list page empty") {
      val res = ctrl.list(params).apply(unsecuredReq)
      status(res) shouldBe Status.OK
    }
  }
}
