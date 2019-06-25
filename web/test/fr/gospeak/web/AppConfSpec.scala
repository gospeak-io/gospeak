package fr.gospeak.web

import org.scalatest.{FunSpec, Matchers}

import scala.util.Success

class AppConfSpec extends FunSpec with Matchers {
  describe("AppConf") {
    it("should load the conf") {
      AppConf.load() shouldBe a[Success[_]]
    }
  }
}
