package fr.gospeak.infra.formats

import fr.gospeak.core.domain.utils.GMapPlace
import fr.gospeak.core.testingutils.Generators._
import fr.gospeak.infra.formats.JsonFormats._
import org.scalatest.prop.PropertyChecks
import org.scalatest.{FunSpec, Matchers}

import scala.util.Success

class JsonFormatsSpec extends FunSpec with Matchers with PropertyChecks {
  describe("JsonFormats") {
    describe("GMapPlace") {
      it("should serialize and parse from/to JSON") {
        forAll { p: GMapPlace =>
          val json = JsonFormats.toJson(p)
          JsonFormats.fromJson[GMapPlace](json) shouldBe Success(p)
        }
      }
    }
  }
}
