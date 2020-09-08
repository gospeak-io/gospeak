package gospeak.infra.services.storage.sql

import gospeak.infra.testingutils.BaseSpec

class TablesSpec extends BaseSpec {
  describe("Tables") {
    it("should not have duplicate name or prefix") {
      val names = Tables.all.map(_.value.query.sql)
      val prefixes = Tables.all.map(_.prefix)
      names.diff(names.distinct) shouldBe List()
      prefixes.diff(prefixes.distinct) shouldBe List()
    }
  }
}

object TablesSpec {
  val socialFields: String = List("facebook", "instagram", "twitter", "linkedIn", "youtube", "meetup", "eventbrite", "slack", "discord", "github").map("social_" + _).mkString(", ")
}
