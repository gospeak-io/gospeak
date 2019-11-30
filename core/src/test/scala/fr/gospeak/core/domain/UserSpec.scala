package fr.gospeak.core.domain

import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers

class UserSpec extends AnyFunSpec with Matchers {
  describe("User") {
    describe("Name") {
      it("should merge firstName and lastName") {
        User.Name("first", "last").value shouldBe "first last"
      }
    }
  }
}
