package fr.gospeak.core.domain.utils

import org.scalatest.{FunSpec, Matchers}

class PasswordSpec extends FunSpec with Matchers {
  describe("Password") {
    it("should not leak its value on toString") {
      Password("aaa").toString should not include "aaa"
    }
    it("should return its value on decode") {
      Password("aaa").decode shouldBe "aaa"
    }
  }
}
