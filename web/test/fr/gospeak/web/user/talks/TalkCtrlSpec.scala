package fr.gospeak.web.user.talks

import java.time.Instant

import com.danielasfregola.randomdatagenerator.RandomDataGenerator
import fr.gospeak.core.domain.User
import fr.gospeak.core.testingutils.Generators._
import fr.gospeak.libs.scalautils.domain.{Email, Page}
import fr.gospeak.web.auth.services.AuthRepo
import fr.gospeak.web.testingutils.Values
import org.scalatest.{BeforeAndAfterEach, FunSpec, Matchers}
import play.api.http.Status
import play.api.test.FakeRequest
import play.api.test.Helpers._

class TalkCtrlSpec extends FunSpec with Matchers with BeforeAndAfterEach with RandomDataGenerator {
  private val params = Page.Params()
  private val db = Values.db
  private val auth = new AuthRepo(db)
  private val now = random[Instant]
  private val userSlug = random[User.Slug]
  private val firstName = random[String].take(30)
  private val lastName = random[String].take(30)
  private val email = random[Email]
  private val ctrl = new TalkCtrl(Values.cc, db, auth)

  override def beforeEach(): Unit = {
    db.createTables().unsafeRunSync()
    val u = db.createUser(userSlug, firstName, lastName, email, now).unsafeRunSync()
    auth.login(u).unsafeRunSync()
  }

  override def afterEach(): Unit = db.dropTables().unsafeRunSync()

  describe("TalkCtrl") {
    describe("list") {
      it("should return 200") {
        val res = ctrl.list(params).apply(FakeRequest())
        status(res) shouldBe Status.OK
        contentAsString(res) should include("""<div class="jumbotron">""")
      }
    }
  }
}
