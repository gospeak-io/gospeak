package gospeak.libs.scala.domain

import gospeak.libs.scala.Crypto
import gospeak.libs.testingutils.BaseSpec

class CryptedSpec extends BaseSpec {
  describe("Crypted") {
    it("should not print its value in toString") {
      Crypted("test").toString shouldBe "*****"
    }
    it("should return its value with decode") {
      val key = Crypto.aesGenerateKey().get
      val crypted = Crypted.from("test", key).get
      crypted.decode(key).get shouldBe "test"
    }
  }
}
