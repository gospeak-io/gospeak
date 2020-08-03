package gospeak.core.services.meetup.domain

import gospeak.core.testingutils.BaseSpec
import gospeak.libs.scala.Crypto

class MeetupTokenSpec extends BaseSpec {
  describe("MeetupToken") {
    it("should serialize and parse to text") {
      val accessToken = "abc"
      val refreshToken = "def"
      val key = Crypto.aesGenerateKey().get
      val token = MeetupToken.from(accessToken, refreshToken, key).get

      val text = MeetupToken.toText(token)
      val parsed = MeetupToken.fromText(text).get

      parsed shouldBe token
      parsed.accessToken.decode(key).get shouldBe accessToken
      parsed.refreshToken.decode(key).get shouldBe refreshToken
    }
    it("should return understandable error when text is not valid") {
      MeetupToken.fromText("aaa").failed.get.getMessage shouldBe "Invalid serialization format of MeetupToken: i�"
      MeetupToken.fromText(Crypto.base64Encode("aaa")).failed.get.getMessage shouldBe "Invalid serialization format of MeetupToken: aaa"
    }
  }
}
