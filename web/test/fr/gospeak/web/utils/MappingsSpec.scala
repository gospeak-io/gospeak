package fr.gospeak.web.utils

import fr.gospeak.web.utils.Mappings._
import org.scalatest.{FunSpec, Matchers}
import play.api.data.FormError
import play.api.data.validation.{Invalid, Valid, ValidationError}

class MappingsSpec extends FunSpec with Matchers {

  case class Value(value: String)

  describe("Mappings") {
    describe("required") {
      val c = required[Value](_.value)
      it("should validate value") {
        c.name shouldBe Some("constraint.required")
        c(Value("aaa")) shouldBe Valid
        c(Value(" ")) shouldBe Invalid(List(ValidationError("error.required")))
        c(Value("")) shouldBe Invalid(List(ValidationError("error.required")))
        c(Value(null)) shouldBe Invalid(List(ValidationError("error.required")))
        c(null.asInstanceOf[Value]) shouldBe Invalid(List(ValidationError("error.required")))
      }
    }
    describe("pattern") {
      val regex = "a+".r
      val c = pattern[Value](regex)(_.value)
      it("should validate value") {
        c.name shouldBe Some("constraint.pattern")
        c(Value("aaa")) shouldBe Valid
        c(Value("bb")) shouldBe Invalid(List(ValidationError("error.pattern", regex)))
        c(Value(" ")) shouldBe Invalid(List(ValidationError("error.pattern", regex)))
        c(Value("")) shouldBe Invalid(List(ValidationError("error.pattern", regex)))
        c(Value(null)) shouldBe Invalid(List(ValidationError("error.pattern", regex)))
        c(null.asInstanceOf[Value]) shouldBe Invalid(List(ValidationError("error.pattern", regex)))
      }
    }
    describe("formatter") {
      val f = formatter[Value](Value, _.value)
      it("should bind & unbind value") {
        val value = Value("aaa")
        f.bind("key", Map("key" -> value.value)) shouldBe Right(value)
        f.unbind("key", value) shouldBe Map("key" -> value.value)
      }
      it("should return required error") {
        f.bind("key", Map()) shouldBe Left(List(FormError("key", List("error.required"))))
      }
    }
  }
}
