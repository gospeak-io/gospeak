package fr.gospeak.web.pages.published.cfps

import fr.gospeak.core.domain.utils.GospeakMessage
import fr.gospeak.libs.scalautils.BasicMessageBus
import fr.gospeak.libs.scalautils.domain.Page
import fr.gospeak.web.domain.{GospeakMessageBus, MessageBuilder}
import fr.gospeak.web.testingutils.CtrlSpec
import org.scalatest.BeforeAndAfterEach
import play.api.http.Status
import play.api.test.Helpers.{status, _}

class CfpCtrlSpec /* extends CtrlSpec with BeforeAndAfterEach {
  private val params = Page.Params()
  private val messageBuilder = new MessageBuilder()
  private val messageBus = new BasicMessageBus[GospeakMessage]()
  private val orgaMessageBus = new GospeakMessageBus(messageBus, messageBuilder)

  private val ctrl = new CfpCtrl(cc, silhouette, db.group, db.cfp, db.talk, db.proposal, db.userRequest, authSrv, emailSrv, orgaMessageBus)

  override def beforeEach(): Unit = db.migrate().unsafeRunSync()

  override def afterEach(): Unit = db.dropTables().unsafeRunSync()

  describe("CfpCtrl") {
    it("should display the cfp list page empty") {
      val res = ctrl.list(params).apply(unsecuredReq)
      status(res) shouldBe Status.OK
    }
  }
} */
