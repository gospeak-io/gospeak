package fr.gospeak.web.services.openapi.models

import cats.data.NonEmptyList
import fr.gospeak.web.services.openapi.error.OpenApiError.ErrorMessage
import fr.gospeak.web.services.openapi.models.utils.Version
import org.scalatest.{FunSpec, Matchers}

class OpenApiSpec extends FunSpec with Matchers {
  private val basicOpenApi = OpenApi(Version(1, 2, 3), Info("My api", None, None, None, None, Version(1), None), None, None, None, None, None, None, None)

  describe("OpenApi") {
    it("should check for duplicate tags") {
      val validOpenApi = basicOpenApi.copy(tags = Some(List(Tag("aaa", None, None, None), Tag("bbb", None, None, None))))
      val invalidOpenApi = basicOpenApi.copy(tags = Some(List(Tag("aaa", None, None, None), Tag("aaa", None, None, None))))
      validOpenApi.hasErrors shouldBe None
      invalidOpenApi.hasErrors shouldBe Some(NonEmptyList.of(ErrorMessage.duplicateValue("aaa", "tags")))
    }
  }
}
