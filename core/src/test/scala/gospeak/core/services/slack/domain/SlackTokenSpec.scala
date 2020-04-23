package gospeak.core.services.slack.domain

import gospeak.core.testingutils.BaseSpec
import gospeak.libs.scala.domain.Crypted

class SlackTokenSpec extends BaseSpec {
  describe("SlackToken") {
    it("should not print its value in toString") {
      SlackToken(Crypted("test")).toString shouldBe "SlackToken(*****)"
    }
  }
}
