package gospeak.libs.youtube.utils

import java.time.Instant

import com.google.api.client.util.DateTime
import gospeak.libs.testingutils.BaseSpec

class YoutubeParserSpec extends BaseSpec {
  describe("YoutubeParser") {
    describe("toInstant") {
      it("should parse google DateTime") {
        val date = DateTime.parseRfc3339("2013-06-17T08:51:45.000Z")
        val instant = Instant.parse("2013-06-17T08:51:45.000Z")
        YoutubeParser.toInstant(date) shouldBe instant
      }
      it("should parse google date String") {
        val date = "2013-06-17T08:51:45Z"
        val instant = Instant.parse("2013-06-17T08:51:45.000Z")
        YoutubeParser.toInstant(date) shouldBe instant
      }
    }
  }
}
