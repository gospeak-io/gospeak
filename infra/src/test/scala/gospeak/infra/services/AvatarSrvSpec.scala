package gospeak.infra.services

import gospeak.core.domain.User
import gospeak.libs.scala.domain.EmailAddress
import org.scalatest.{FunSpec, Matchers}
import gospeak.libs.scala.Extensions._

class AvatarSrvSpec extends FunSpec with Matchers {
  private val srv = new AvatarSrv

  describe("AvatarSrv") {
    it("should generate a gravatar url") {
      val url = srv.getDefault(EmailAddress.from("example@mail.com").get, User.Slug.from("example").get).value
      url shouldBe "https://secure.gravatar.com/avatar/fbf2b9cfc0a472389f3620e471bdf0e9?size=150&default=https%3A%2F%2Fapi.adorable.io%2Favatars%2F150%2Fexample.png"
    }
  }
}
