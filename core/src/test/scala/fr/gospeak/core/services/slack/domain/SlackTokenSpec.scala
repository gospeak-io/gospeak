package fr.gospeak.core.services.slack.domain

import fr.gospeak.libs.scalautils.domain.Crypted
import org.scalatest.{FunSpec, Matchers}

class SlackTokenSpec extends FunSpec with Matchers {
  describe("SlackToken") {
    it("should not print its value in toString") {
      SlackToken(Crypted("test")).toString shouldBe "SlackToken(*****)"
    }
  }
}
