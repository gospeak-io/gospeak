package gospeak.web.utils

import java.time.Instant

import gospeak.web.testingutils.BaseSpec

import scala.concurrent.duration._

class FormatsSpec extends BaseSpec {
  private val i = Instant.ofEpochMilli(1549115209899L)

  describe("Formats") {
    describe("date") {
      it("should format instant depending on locale") {
        Formats.date(i) shouldBe "02 Feb 2019"
      }
    }
    describe("datetime") {
      it("should format instant depending on locale") {
        Formats.datetime(i) shouldBe "02 Feb 2019 at 14:46 (UTC)"
      }
    }
    describe("timeAgo") {
      it("should display time for any unit") {
        Formats.timeAgo(Duration(5, NANOSECONDS)) shouldBe "just now"
        Formats.timeAgo(Duration(5, MICROSECONDS)) shouldBe "just now"
        Formats.timeAgo(Duration(5, MILLISECONDS)) shouldBe "just now"
        Formats.timeAgo(Duration(5, SECONDS)) shouldBe "in 5 seconds"
        Formats.timeAgo(Duration(5, MINUTES)) shouldBe "in 5 minutes"
        Formats.timeAgo(Duration(5, HOURS)) shouldBe "in 5 hours"
        Formats.timeAgo(Duration(5, DAYS)) shouldBe "in 5 days"
        Formats.timeAgo(Duration(18, DAYS)) shouldBe "in 2 weeks"
        Formats.timeAgo(Duration(63, DAYS)) shouldBe "in 2 months"
        Formats.timeAgo(Duration(900, DAYS)) shouldBe "in 2 years"
      }
      it("should manage singular/plural and positive/negative durations") {
        Formats.timeAgo(Duration(-2, SECONDS)) shouldBe "2 seconds ago"
        Formats.timeAgo(Duration(-1, SECONDS)) shouldBe "1 second ago"
        Formats.timeAgo(Duration(0, SECONDS)) shouldBe "just now"
        Formats.timeAgo(Duration(1, SECONDS)) shouldBe "in 1 second"
        Formats.timeAgo(Duration(2, SECONDS)) shouldBe "in 2 seconds"
      }
      it("should manage java Instant") {
        Formats.timeAgo(i, i.plusSeconds(5)) shouldBe Formats.timeAgo(Duration(-5, SECONDS))
      }
    }
    describe("round") {
      it("should round duration ton one unit") {
        Formats.round(Duration(3, DAYS).plus(Duration(1, HOURS))) shouldBe Duration(3, DAYS)
      }
      it("should manage negative numbers") {
        Formats.round(Duration(-3, DAYS).plus(Duration(-1, HOURS))) shouldBe Duration(-3, DAYS)
        Formats.round(Duration(-3, DAYS).plus(Duration(1, HOURS))) shouldBe Duration(-2, DAYS)
      }
    }
    describe("duration") {
      it("should display durations") {
        Formats.duration(3.millis) shouldBe "0:00"
        Formats.duration(5.seconds) shouldBe "0:05"
        Formats.duration(46.seconds) shouldBe "0:46"
        Formats.duration(5.minutes.plus(16.seconds)) shouldBe "5:16"
        Formats.duration(12.minutes.plus(2.seconds)) shouldBe "12:02"
        Formats.duration(2.hours.plus(23.minutes).plus(18.seconds)) shouldBe "2:23:18"
        Formats.duration(11.hours.plus(3.minutes).plus(7.seconds)) shouldBe "11:03:07"
      }
    }
  }
}
