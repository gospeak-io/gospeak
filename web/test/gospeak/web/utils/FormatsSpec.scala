package fr.gospeak.web.utils

import java.time.Instant

import org.scalatest.{FunSpec, Matchers}

import scala.concurrent.duration._

class FormatsSpec extends FunSpec with Matchers {
  private val i = Instant.ofEpochMilli(1549115209899L)

  describe("Formats") {
    describe("date") {
      it("should format instant depending on locale") {
        Formats.date(i) shouldBe "02 Feb 2019"
      }
    }
    describe("datetime") {
      it("should format instant depending on locale") {
        Formats.datetime(i) shouldBe "02 Feb 2019 at 14:46:49.899 (UTC)"
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
  }
}
