package fr.gospeak.libs.scalautils

import fr.gospeak.libs.scalautils.Extensions._
import org.scalatest.{FunSpec, Matchers}

import scala.util.{Failure, Success}

class ExtensionsSpec extends FunSpec with Matchers {
  describe("Extensions") {
    describe("OptionExtension") {
      it("should convert an Option to a Try") {
        val e = new Exception
        Some(1).toTry(e) shouldBe Success(1)
        None.toTry(e) shouldBe Failure(e)
      }
    }
  }
}
