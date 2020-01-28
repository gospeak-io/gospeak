package gospeak.core.domain

import org.scalatest.{FunSpec, Matchers}

class UserSpec extends FunSpec with Matchers {
  describe("User") {
    describe("Name") {
      it("should merge firstName and lastName") {
        User.Name("first", "last").value shouldBe "first last"
      }
    }
  }
}
