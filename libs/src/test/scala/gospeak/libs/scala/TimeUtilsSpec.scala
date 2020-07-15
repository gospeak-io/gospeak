package gospeak.libs.scala

import gospeak.libs.testingutils.BaseSpec

import scala.concurrent.duration._
import scala.util.{Failure, Success}

class TimeUtilsSpec extends BaseSpec {
  describe("toFiniteDuration") {
    it("should parse a finite duration string") {
      TimeUtils.toFiniteDuration("PT10M") shouldBe Success(10.minutes)
      TimeUtils.toFiniteDuration("Inf") shouldBe a[Failure[_]]
    }
  }
}
