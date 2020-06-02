package gospeak.web.utils

import java.time.Instant

import gospeak.web.testingutils.{BaseSpec, Values}
import play.api.mvc.AnyContent

import scala.concurrent.duration._

class packageTest extends BaseSpec {
  private val i = Instant.ofEpochMilli(1549115209899L)
  private implicit val req: UserReq[AnyContent] = Values.userReq

  describe("utils") {
    describe("RichInstant") {
      it("should format as date") {
        i.asDate shouldBe "02 Feb 2019"
      }
      it("should format as datetime") {
        i.asDatetime shouldBe "02 Feb 2019 at 14:46 (UTC)"
      }
    }
    describe("RichFiniteDuration") {
      it("should round to highest unit") {
        Duration(3, DAYS).plus(Duration(1, HOURS)).round shouldBe Duration(3, DAYS)
      }
      it("should also round negative numbers") {
        Duration(-3, DAYS).plus(Duration(-1, HOURS)).round shouldBe Duration(-3, DAYS)
        Duration(-3, DAYS).plus(Duration(1, HOURS)).round shouldBe Duration(-2, DAYS)
      }
      it("should display durations") {
        3.millis.format shouldBe "0:00"
        5.seconds.format shouldBe "0:05"
        46.seconds.format shouldBe "0:46"
        5.minutes.plus(16.seconds).format shouldBe "5:16"
        12.minutes.plus(2.seconds).format shouldBe "12:02"
        2.hours.plus(23.minutes).plus(18.seconds).format shouldBe "2:23:18"
        11.hours.plus(3.minutes).plus(7.seconds).format shouldBe "11:03:07"
      }
    }
    describe("private functions") {
      describe("timeAgo") {
        it("should display time for any unit") {
          timeAgo(Duration(5, NANOSECONDS)) shouldBe "just now"
          timeAgo(Duration(5, MICROSECONDS)) shouldBe "just now"
          timeAgo(Duration(5, MILLISECONDS)) shouldBe "just now"
          timeAgo(Duration(5, SECONDS)) shouldBe "in 5 seconds"
          timeAgo(Duration(5, MINUTES)) shouldBe "in 5 minutes"
          timeAgo(Duration(5, HOURS)) shouldBe "in 5 hours"
          timeAgo(Duration(5, DAYS)) shouldBe "in 5 days"
          timeAgo(Duration(18, DAYS)) shouldBe "in 2 weeks"
          timeAgo(Duration(63, DAYS)) shouldBe "in 2 months"
          timeAgo(Duration(900, DAYS)) shouldBe "in 2 years"
        }
        it("should manage singular/plural and positive/negative durations") {
          timeAgo(Duration(-2, SECONDS)) shouldBe "2 seconds ago"
          timeAgo(Duration(-1, SECONDS)) shouldBe "1 second ago"
          timeAgo(Duration(0, SECONDS)) shouldBe "just now"
          timeAgo(Duration(1, SECONDS)) shouldBe "in 1 second"
          timeAgo(Duration(2, SECONDS)) shouldBe "in 2 seconds"
        }
      }
    }
  }
}
