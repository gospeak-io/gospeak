package fr.gospeak.libs.scalautils.domain

import fr.gospeak.libs.scalautils.testingutils.Generators._
import org.scalatest.{FunSpec, Matchers}
import org.scalatestplus.scalacheck.ScalaCheckPropertyChecks

import scala.concurrent.duration.FiniteDuration

class TimePeriodSpec extends FunSpec with Matchers with ScalaCheckPropertyChecks {
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
