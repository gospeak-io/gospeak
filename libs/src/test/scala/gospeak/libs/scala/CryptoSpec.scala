package gospeak.libs.scala

import gospeak.libs.scala.Crypto.{AesEncrypted, AesSecretKey}
import gospeak.libs.testingutils.BaseSpec
import org.scalatestplus.scalacheck.ScalaCheckPropertyChecks

class CryptoSpec extends BaseSpec with ScalaCheckPropertyChecks {
  describe("Crypto") {
    describe("base64") {
      it("should encode and decode base64") {
        forAll { v: String =>
          val base64 = Crypto.base64Encode(v)
          Crypto.base64Decode(base64).get shouldBe v
        }
      }
      it("should encode 'toto'") {
        Crypto.base64Encode("toto") shouldBe "dG90bw=="
      }
    }
    describe("md5") {
      it("should compute a md5") {
        Crypto.md5("toto") shouldBe "f71dbe52628a3f83a77ab494817525c6"
      }
    }
    describe("sha1") {
      it("should compute a sha1") {
        Crypto.sha1("toto") shouldBe "0b9c2625dc21ef05f6ad4ddf47c5f203837aa32c"
      }
    }
    describe("aes") {
      it("should generate random numbers") {
        val r1 = Crypto.secureRandom().get
        val r2 = Crypto.secureRandom().get
        r1 should not be r2
      }
      it("should encrypt and decrypt a value") {
        forAll { v: String =>
          val key = Crypto.aesGenerateKey().get
          val encrypted = Crypto.aesEncrypt(v, key).get
          Crypto.aesDecrypt(encrypted, key).get shouldBe v
        }
      }
      it("should decrypt 'toto'") {
        val key = AesSecretKey("kid1+XM7ayXTsw+1Q2k67g==")
        Crypto.aesDecrypt(AesEncrypted("r16mW6H0Lchn4OJZ2cDunumBJ+E="), key).get shouldBe "toto"
        Crypto.aesDecrypt(AesEncrypted("bTWtr9kAmDpCkVX81hNv5tkw1bI="), key).get shouldBe "toto"
      }
    }
  }
}
