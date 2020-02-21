package gospeak.libs.scala

import gospeak.libs.scala.Extensions._
import gospeak.libs.scala.domain.CustomException

import scala.util.{Failure, Success}
import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers

class ExtensionsSpec extends AnyFunSpec with Matchers {
  describe("Extensions") {
    describe("TraversableOnceExtension") {
      describe("swap") {
        val list = Seq(1, 2, 3, 4, 5)
        describe("before") {
          it("should move an element one place before") {
            list.swap(elt = 3) shouldBe Seq(1, 3, 2, 4, 5)
          }
          it("should do nothing for the first element") {
            list.swap(elt = 1) shouldBe Seq(1, 2, 3, 4, 5)
          }
          it("should work for the last element") {
            list.swap(elt = 5) shouldBe Seq(1, 2, 3, 5, 4)
          }
          it("should do nothing if element is not found") {
            list.swap(elt = 6) shouldBe Seq(1, 2, 3, 4, 5)
          }
          it("should move every element that match") {
            val list = Seq(1, 2, 3, 2, 2, 3)
            list.swap(elt = 2) shouldBe Seq(2, 1, 2, 3, 2, 3)
          }
        }
        describe("after") {
          it("should move an element one place after") {
            list.swap(elt = 3, before = false) shouldBe Seq(1, 2, 4, 3, 5)
          }
          it("should do nothing for the last element") {
            list.swap(elt = 5, before = false) shouldBe Seq(1, 2, 3, 4, 5)
          }
          it("should work for the first element") {
            list.swap(elt = 1, before = false) shouldBe Seq(2, 1, 3, 4, 5)
          }
          it("should do nothing if element is not found") {
            list.swap(elt = 6, before = false) shouldBe Seq(1, 2, 3, 4, 5)
          }
          it("should move every element that match") {
            val list = Seq(1, 2, 3, 2, 2, 3)
            list.swap(elt = 2, before = false) shouldBe Seq(1, 3, 2, 2, 2, 3)
          }
        }
      }
    }
    describe("TraversableOnceOptionExtension") {
      it("should invert Seq & Option with sequence") {
        Seq(Option(1), Option(2)).sequence shouldBe Option(Seq(1, 2))
        Seq(Option(1), Option(2), None).sequence shouldBe None
      }
    }
    describe("MapOptionExtension") {
      it("should invert Map & Option with sequence") {
        Map(1 -> Option(1), 2 -> Option(2)).sequence shouldBe Option(Map(1 -> 1, 2 -> 2))
        Map(1 -> Option(1), 2 -> Option(2), 3 -> None).sequence shouldBe None
      }
    }
    describe("OptionExtension") {
      describe("toTry") {
        it("should convert an Option to a Try") {
          val e = CustomException("")
          Some(1).toTry(e) shouldBe Success(1)
          None.toTry(e) shouldBe Failure(e)
        }
      }
    }
  }
}
