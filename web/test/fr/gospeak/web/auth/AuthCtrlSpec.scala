package fr.gospeak.web.auth

import fr.gospeak.infra.services.ConsoleEmailSrv
import fr.gospeak.web.auth.services.{AuthRepo, AuthSrv}
import fr.gospeak.web.testingutils.CtrlSpec
import org.scalatest.BeforeAndAfterEach
import play.api.http.Status
import play.api.mvc.AnyContentAsFormUrlEncoded
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
      it("should create a user and credentials then login") {
        val res = ctrl.doSignup().apply(unsecuredReq.withBody(AnyContentAsFormUrlEncoded(Map(
          "slug" -> Seq("slug"),
          "first-name" -> Seq("first"),
          "last-name" -> Seq("last"),
          "email" -> Seq("test@mail.com"),
          "password" -> Seq("admin")
        ))))
        status(res) shouldBe Status.SEE_OTHER
        headers(res) should contain key "Location"
        contentAsString(res) shouldBe ""
      }
      it("should find the user, create credentials then login") {

      }
      it("should fail if credentials already exist") {

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
