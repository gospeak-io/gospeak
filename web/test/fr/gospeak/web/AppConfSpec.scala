package fr.gospeak.web

import com.typesafe.config.{Config, ConfigFactory}
import org.scalatest.{FunSpec, Matchers}

import scala.util.Success

class AppConfSpec extends FunSpec with Matchers {
  describe("AppConf") {
    it("should load the conf") {
      AppConf.load(ConfigFactory.load().withFallback(AppConfSpec.local)) shouldBe a[Success[_]]
    }
  }
}

object AppConfSpec {
  val local: Config = ConfigFactory.parseString(
    """meetup.key = "aaa"
      |meetup.secret = "aaa"
      |""".stripMargin)
}
