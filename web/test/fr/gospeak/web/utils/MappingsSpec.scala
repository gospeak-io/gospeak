package fr.gospeak.web.utils

import java.time.Instant

import fr.gospeak.core.domain.utils.GMapPlace
import fr.gospeak.core.testingutils.Generators._
import fr.gospeak.libs.scalautils.CustomException
import fr.gospeak.web.utils.Mappings.Utils._
import fr.gospeak.web.utils.Mappings._
import org.scalatest.prop.PropertyChecks
import org.scalatest.{FunSpec, Matchers}
import play.api.data.FormError
import play.api.data.validation.{Invalid, Valid, ValidationError}

import scala.util.{Failure, Success, Try}

class MappingsSpec extends FunSpec with Matchers with PropertyChecks {

  case class Value(value: String)

  object Value {
    def from(in: String): Try[Value] = {
      if (in.nonEmpty && in.length < 4) Success(Value(in))
      else Failure(CustomException("Wrong format"))
    }
  }

  case class LongValue(value: Long)

  describe("Mappings") {
    describe("gMapPlace") {
      it("should bind & unbind a GMapPlace") {
        forAll { p: GMapPlace =>
          val data = gMapPlace.unbind(p)
          gMapPlace.bind(data) shouldBe Right(p)
        }
      }
    }
    describe("Utils") {
      describe("required") {
        val c = required[Value](_.value)
        it("should validate value") {
          c.name shouldBe Some("constraint.required")
          c(Value("aaa")) shouldBe Valid
          c(Value(" ")) shouldBe Invalid(List(ValidationError(requiredError)))
          c(Value("")) shouldBe Invalid(List(ValidationError(requiredError)))
        }
      }
      describe("pattern") {
        val regex = "a+".r
        val c = pattern[Value](regex)(_.value)
        it("should validate value") {
          c.name shouldBe Some("constraint.pattern")
          c(Value("aaa")) shouldBe Valid
          c(Value("bb")) shouldBe Invalid(List(ValidationError(patternError, regex)))
          c(Value(" ")) shouldBe Invalid(List(ValidationError(patternError, regex)))
          c(Value("")) shouldBe Invalid(List(ValidationError(patternError, regex)))
        }
      }
      describe("stringFormatter") {
        val f = stringFormatter[Value](Value(_), _.value)
        it("should bind & unbind value") {
          val value = Value("aaa")
          f.bind("key", Map("key" -> value.value)) shouldBe Right(value)
          f.unbind("key", value) shouldBe Map("key" -> value.value)
        }
        it("should return required error") {
          f.bind("key", Map()) shouldBe Left(List(FormError("key", List(requiredError))))
        }
      }
      describe("longFormatter") {
        val f = longFormatter[LongValue](LongValue, _.value)
        it("should bind & unbind value") {
          val value = LongValue(12)
          f.bind("key", Map("key" -> value.value.toString)) shouldBe Right(value)
          f.unbind("key", value) shouldBe Map("key" -> value.value.toString)
        }
        it("should return format error") {
          f.bind("key", Map("key" -> "aa")) shouldBe Left(List(FormError("key", List(numberError), "For input string: \"aa\"")))
        }
        it("should return required error") {
          f.bind("key", Map()) shouldBe Left(List(FormError("key", List(requiredError))))
        }
      }
      describe("instantFormatter") {
        it("should bind & unbind value") {
          val value = "2019-05-24T19:00"
          val parsed = Instant.parse(value + ":00Z")
          instantFormatter.bind("key", Map("key" -> value)) shouldBe Right(parsed)
          instantFormatter.unbind("key", parsed) shouldBe Map("key" -> value)
        }
        it("should return format error") {
          instantFormatter.bind("key", Map("key" -> "aa")) shouldBe Left(List(FormError("key", List(datetimeError), "Text 'aa' could not be parsed at index 0")))
        }
        it("should return required error") {
          instantFormatter.bind("key", Map()) shouldBe Left(List(FormError("key", List(requiredError))))
        }
      }
      describe("stringTryFormatter") {
        val f = stringTryFormatter[Value](Value.from, _.value)
        it("should bind & unbind value") {
          val value = Value("aaa")
          f.bind("key", Map("key" -> value.value)) shouldBe Right(value)
          f.unbind("key", value) shouldBe Map("key" -> value.value)
        }
        it("should return format error") {
          f.bind("key", Map("key" -> "")) shouldBe Left(List(FormError("key", List(formatError), "Wrong format")))
        }
        it("should return required error") {
          f.bind("key", Map()) shouldBe Left(List(FormError("key", List(requiredError))))
        }
      }
    }
  }
}
