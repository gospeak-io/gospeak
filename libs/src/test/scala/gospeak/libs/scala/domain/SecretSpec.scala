package gospeak.libs.scala.domain

import gospeak.libs.testingutils.BaseSpec

class SecretSpec extends BaseSpec {
  describe("Password") {
    it("should not leak its value on toString") {
      Secret("aaa").toString should not include "aaa"
    }
    it("should return its value on decode") {
      Secret("aaa").decode shouldBe "aaa"
    }
  }
}
