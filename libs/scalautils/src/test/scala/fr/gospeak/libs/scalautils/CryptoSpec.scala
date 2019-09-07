package fr.gospeak.libs.scalautils

import fr.gospeak.libs.scalautils.Crypto.{AesEncrypted, AesSecretKey}
import org.scalatest.{FunSpec, Matchers}
import org.scalatestplus.scalacheck.ScalaCheckPropertyChecks

class CryptoSpec extends FunSpec with Matchers with ScalaCheckPropertyChecks {
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
