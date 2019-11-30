package fr.gospeak.libs.scalautils.domain

import fr.gospeak.libs.scalautils.testingutils.Generators._
import org.scalatestplus.scalacheck.ScalaCheckPropertyChecks

import scala.concurrent.duration.FiniteDuration
import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers

class TimePeriodSpec extends AnyFunSpec with Matchers with ScalaCheckPropertyChecks {
  describe("TimePeriod") {
    it("build and transform to FiniteDuration") {
      forAll { v: FiniteDuration =>
        val p = TimePeriod.from(v)
        val r = p.toDuration.get
        r shouldBe v
      }
    }
    it("should parse and serialize to String") {
      forAll { v: TimePeriod =>
        val s = v.value
        val p = TimePeriod.from(s).get
        p shouldBe v
      }
    }
  }
}
