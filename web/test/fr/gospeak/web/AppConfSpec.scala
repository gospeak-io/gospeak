package fr.gospeak.web

import com.typesafe.config.{Config, ConfigFactory}

import scala.util.Success
import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers

class AppConfSpec extends AnyFunSpec with Matchers {
  describe("AppConf") {
    it("should load the conf") {
      AppConf.load(ConfigFactory.load()) shouldBe a[Success[_]]
    }
  }
}
