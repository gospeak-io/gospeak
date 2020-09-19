package gospeak.libs.scala

import gospeak.libs.scala.StringUtils._
import gospeak.libs.testingutils.BaseSpec

class StringUtilsSpec extends BaseSpec {
  describe("StringUtils") {
    describe("leftPad") {
      it("should pad Strings") {
        leftPad("test") shouldBe "      test"
        leftPad("test", 3) shouldBe "test"
      }
    }
    describe("rightPad") {
      it("should pad Strings") {
        rightPad("test") shouldBe "test      "
        rightPad("test", 3) shouldBe "test"
      }
    }
    describe("removeDiacritics") {
      it("should remove special chars") {
        removeDiacritics("téléphone") shouldBe "telephone"
      }
    }
    describe("slugify") {
      it("should remove special chars and replace the") {
        slugify("HumanTalks + Paris") shouldBe "humantalks-paris"
      }
    }
    describe("stripIdenticalPrefix") {
      it("should remove the identical prefix") {
        val res = stripIdenticalPrefix("Hello Lou", "Hello Tom")
        res shouldBe("[..6..]Lou", "[..6..]Tom")
      }
      it("should do nothing when prefix is not identical") {
        val res = stripIdenticalPrefix("1 jambon", "2 jambon")
        res shouldBe("1 jambon", "2 jambon")
      }
    }
    describe("stripIdenticalSuffix") {
      it("should remove the identical suffix") {
        val res = stripIdenticalSuffix("1 jambon", "2 jambon")
        res shouldBe("1[..7..]", "2[..7..]")
      }
      it("should do nothing when suffix is not identical") {
        val res = stripIdenticalSuffix("Hello Lou", "Hello Tom")
        res shouldBe("Hello Lou", "Hello Tom")
      }
    }
  }
}
