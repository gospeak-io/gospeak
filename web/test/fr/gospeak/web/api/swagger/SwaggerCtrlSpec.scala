package fr.gospeak.web.api.swagger

import org.scalatest.{FunSpec, Matchers}

import scala.util.Success

class SwaggerCtrlSpec extends FunSpec with Matchers {
  describe("SwaggerCtrl") {
    it("should load the spec") {
      SwaggerCtrl.loadSwaggerSpec() shouldBe a[Success[_]]
    }
  }
}
