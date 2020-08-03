package gospeak.libs.meetup.domain

import java.time.LocalDateTime

import gospeak.libs.testingutils.BaseSpec

class MeetupEventSpec extends BaseSpec {
  describe("MeetupEvent") {
    it("should compute a local date from timestamp") {
      MeetupEvent.toLocalDate(1562691600000L, None) shouldBe LocalDateTime.of(2019, 7, 9, 17, 0)
      MeetupEvent.toLocalDate(1562691600000L, Some(7200000)) shouldBe LocalDateTime.of(2019, 7, 9, 19, 0)
    }
    it("should compute a local date from string") {
      MeetupEvent.toLocalDate("2019-07-09", None) shouldBe LocalDateTime.of(2019, 7, 9, 0, 0)
      MeetupEvent.toLocalDate("2019-07-09", Some("19:00")) shouldBe LocalDateTime.of(2019, 7, 9, 19, 0)
    }
  }
}
