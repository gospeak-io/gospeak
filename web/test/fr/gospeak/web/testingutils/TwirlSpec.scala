package fr.gospeak.web.testingutils

import fr.gospeak.core.domain.User
import org.scalatest.{FunSpec, Matchers}
import play.api.mvc.{AnyContentAsEmpty, Flash}
import play.api.test.FakeRequest

trait TwirlSpec extends FunSpec with Matchers {
  protected implicit val user: Option[User] = None
  protected implicit val req: FakeRequest[AnyContentAsEmpty.type] = FakeRequest()
  protected implicit val flash: Flash = req.flash
}
