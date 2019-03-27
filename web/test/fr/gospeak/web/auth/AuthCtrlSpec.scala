package fr.gospeak.web.auth

import fr.gospeak.infra.services.ConsoleEmailSrv
import fr.gospeak.web.auth.services.{AuthRepo, AuthSrv}
import fr.gospeak.web.testingutils.CtrlSpec
import org.scalatest.BeforeAndAfterEach
import play.api.http.Status
import play.api.test.Helpers._

class AuthCtrlSpec extends CtrlSpec with BeforeAndAfterEach {
  private val authRepo = new AuthRepo(db)
  private val authSrv = AuthSrv(conf.auth.cookie, silhouette, db, authRepo, clock)
  private val ctrl = new AuthCtrl(cc, silhouette, db, authSrv, new ConsoleEmailSrv())

  override def beforeEach(): Unit = db.createTables().unsafeRunSync()

  override def afterEach(): Unit = db.dropTables().unsafeRunSync()

  describe("AuthCtrl") {
    describe("signup") {
      it("should return form when not authenticated") {
        val res = ctrl.signup().apply(unsecuredReq)
        status(res) shouldBe Status.OK
      }
      it("should redirect to user home when authenticated") {
        val res = ctrl.signup().apply(securedReq)
        status(res) shouldBe Status.SEE_OTHER
      }
    }
    describe("login") {
      it("should return form when not authenticated") {
        val res = ctrl.login().apply(unsecuredReq)
        status(res) shouldBe Status.OK
      }
      it("should redirect to user home when authenticated") {
        val res = ctrl.login().apply(securedReq)
        status(res) shouldBe Status.SEE_OTHER
      }
    }
    describe("passwordReset") {
      it("should return form when not authenticated") {
        val res = ctrl.passwordReset().apply(unsecuredReq)
        status(res) shouldBe Status.OK
      }
      it("should redirect to user home when authenticated") {
        val res = ctrl.passwordReset().apply(securedReq)
        status(res) shouldBe Status.SEE_OTHER
      }
    }
  }
}
