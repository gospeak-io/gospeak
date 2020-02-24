package gospeak.infra.services.storage.sql

import org.scalatest.{FunSpec, Matchers}

class TablesSpec extends FunSpec with Matchers {
  describe("Tables") {
    it("should not have duplicate name or prefix") {
      val names = Tables.all.map(_.value.query.sql)
      val prefixes = Tables.all.map(_.prefix)
      names.diff(names.distinct) shouldBe Seq()
      prefixes.diff(prefixes.distinct) shouldBe Seq()
    }
  }
}

object TablesSpec {
  val socialFields: String = Seq("facebook", "instagram", "twitter", "linkedIn", "youtube", "meetup", "eventbrite", "slack", "discord", "github").map("social_" + _).mkString(", ")
}
