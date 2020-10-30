package gospeak.libs.scala

import java.time.temporal.ChronoUnit
import java.time.{Instant, LocalDate, LocalDateTime}

import gospeak.libs.testingutils.BaseSpec

import scala.concurrent.duration._
import scala.util.{Failure, Success}

class TimeUtilsSpec extends BaseSpec {
  describe("toFiniteDuration") {
    it("should transform times") {
      val i = Instant.parse("2020-08-05T10:20:34Z")
      val ldt = LocalDateTime.of(2020, 8, 5, 10, 20, 34)
      val ld = LocalDate.of(2020, 8, 5)
      TimeUtils.toInstant(ldt) shouldBe i
      TimeUtils.toInstant(ld) shouldBe i.minus(10, ChronoUnit.HOURS).minus(20, ChronoUnit.MINUTES).minusSeconds(34)
      TimeUtils.toLocalDateTime(i) shouldBe ldt
      TimeUtils.toLocalDate(i) shouldBe ld
    }
    it("should parse a finite duration string") {
      TimeUtils.toFiniteDuration("PT10M") shouldBe Success(10.minutes)
      TimeUtils.toFiniteDuration("Inf") shouldBe a[Failure[_]]
    }
  }
}
