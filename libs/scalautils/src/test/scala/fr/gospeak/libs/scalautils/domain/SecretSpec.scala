package fr.gospeak.libs.scalautils.domain

import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers

class SecretSpec extends AnyFunSpec with Matchers {
  describe("Password") {
    it("should not leak its value on toString") {
      Secret("aaa").toString should not include "aaa"
    }
    it("should return its value on decode") {
      Secret("aaa").decode shouldBe "aaa"
    }
  }
}
