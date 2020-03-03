package gospeak.core.domain

import gospeak.libs.scala.Extensions._
import gospeak.libs.scala.domain.EmailAddress
import org.scalatest.{FunSpec, Matchers}

class UserSpec extends FunSpec with Matchers {
  describe("User") {
    describe("Name") {
      it("should merge firstName and lastName") {
        User.Name("first", "last").value shouldBe "first last"
      }
    }
    describe("companyFromEmail") {
      it("should extract company name from email") {
        User.companyFromEmail(EmailAddress.from("loic@mail.com").get) shouldBe None
        User.companyFromEmail(EmailAddress.from("loic@gmail.com").get) shouldBe None
        User.companyFromEmail(EmailAddress.from("loic@yahoo.fr").get) shouldBe None
        User.companyFromEmail(EmailAddress.from("loic@hotmail.fr").get) shouldBe None
        User.companyFromEmail(EmailAddress.from("loic@protonmail.com").get) shouldBe None
        User.companyFromEmail(EmailAddress.from("loic@outlook.com").get) shouldBe None
        User.companyFromEmail(EmailAddress.from("loic@mailoo.org").get) shouldBe None
        User.companyFromEmail(EmailAddress.from("alexandre-gindre@missing-email.com").get) shouldBe None
        User.companyFromEmail(EmailAddress.from("loic@knuchel.org").get) shouldBe Some("knuchel")
        User.companyFromEmail(EmailAddress.from("loic@octo.com").get) shouldBe Some("octo")
        User.companyFromEmail(EmailAddress.from("loic@doctolib.com").get) shouldBe Some("doctolib")
      }
    }
  }
}
