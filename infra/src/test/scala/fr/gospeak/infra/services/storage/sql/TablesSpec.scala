package fr.gospeak.infra.services.storage.sql

import org.scalatest.{FunSpec, Matchers}

class TablesSpec extends FunSpec with Matchers {
  describe("Tables") {
    it("should not have duplicate name or prefix") {
      Tables.all.map(_.name).distinct shouldBe Tables.all.map(_.name)
      Tables.all.map(_.prefix).distinct shouldBe Tables.all.map(_.prefix)
    }
  }
}
