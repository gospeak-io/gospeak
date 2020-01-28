package gospeak.web.services.openapi.models.utils

import gospeak.web.services.openapi.error.OpenApiError
import org.scalatest.{FunSpec, Matchers}

class VersionSpec extends FunSpec with Matchers {
  describe("Version") {
    it("should parse valid Versions") {
      Version.from("1.2.3") shouldBe Right(Version(1, 2, 3))
      Version.from("1.2") shouldBe Right(Version(1, 2, 0))
      Version.from("1") shouldBe Right(Version(1, 0, 0))
    }
    it("should fail on invalid Versions") {
      Version.from("1.2.c") shouldBe a[Left[_, _]]
      Version.from("1.b.3") shouldBe a[Left[_, _]]
      Version.from("a.2.3") shouldBe a[Left[_, _]]
      Version.from("a") shouldBe Left(OpenApiError.badFormat("a", "Version", "1.2.3"))
    }
  }
}
