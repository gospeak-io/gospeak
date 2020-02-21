package gospeak.core.services.slack.domain

import gospeak.libs.scala.domain.Crypted
import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers

class SlackTokenSpec extends AnyFunSpec with Matchers {
  describe("SlackToken") {
    it("should not print its value in toString") {
      SlackToken(Crypted("test")).toString shouldBe "SlackToken(*****)"
    }
  }
}
