package gospeak.web

import com.typesafe.config.ConfigFactory
import gospeak.web.testingutils.BaseSpec

import scala.util.Success

class AppConfSpec extends BaseSpec {
  describe("AppConf") {
    it("should load the conf") {
      AppConf.load(ConfigFactory.load()) shouldBe a[Success[_]]
    }
  }
}
