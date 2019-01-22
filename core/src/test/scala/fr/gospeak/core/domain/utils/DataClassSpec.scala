package fr.gospeak.core.domain.utils

import org.scalatest.{FunSpec, Matchers}

import scala.util.{Failure, Success}

class DataClassSpec extends FunSpec with Matchers {

  class Id(value: String) extends DataClass(value)

  describe("DataClass") {
    it("should print value in toString") {
      val id = new Id("toto")
      id.toString shouldBe id.value
    }
    it("should define equality") {
      new Id("toto") shouldBe new Id("toto")
      new Id("toto") should not be new Id("tata")
    }
  }
  describe("UuidIdBuilder") {
    val builder = new UuidIdBuilder[Id]("Id", new Id(_)) {}

    it("should generate random value") {
      builder.generate().value.length shouldBe 36
    }
    it("should build an Id from a String") {
      builder.from("toto") shouldBe a[Failure[_]]
      builder.from("792886f8-d92a-4066-ad17-f92a0a93a42c") shouldBe a[Success[_]]
    }
    it("should check for correctness") {
      builder.errors("toto") should not be empty
      builder.errors("792886f8-d92a-4066-ad17-f92a0a93a42c") shouldBe empty
    }
  }
}
