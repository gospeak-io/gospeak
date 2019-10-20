package fr.gospeak.infra.services.storage.sql

import org.scalatest.{FunSpec, Matchers}

class TablesSpec extends FunSpec with Matchers {
  describe("Tables") {
    it("should not have duplicate name or prefix") {
      val names = Tables.all.map(_.value)
      val prefixes = Tables.all.map(_.prefix)
      names.diff(names.distinct) shouldBe Seq()
      prefixes.diff(prefixes.distinct) shouldBe Seq()
    }
  }
}
