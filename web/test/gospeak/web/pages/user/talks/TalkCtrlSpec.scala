package gospeak.web.pages.user.talks

import gospeak.core.domain.messages.Message
import gospeak.libs.scala.BasicMessageBus
import gospeak.libs.scala.domain.Page
import gospeak.web.services.MessageSrv
import gospeak.web.testingutils.CtrlSpec
import org.scalatest.BeforeAndAfterEach
import play.api.http.Status
import play.api.test.Helpers._

class TalkCtrlSpec extends CtrlSpec with BeforeAndAfterEach {
  private val params = Page.Params()
  private val messageSrv = new MessageSrv(db.group, db.cfp, db.venue, db.proposal, db.sponsor, db.user)
  private val messageBus = new BasicMessageBus[Message]()
  private val ctrl = new TalkCtrl(cc, silhouette, conf, db.user, db.userRequest, db.talk, db.externalEvent, db.externalProposal, emailSrv, messageSrv, messageBus)

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
