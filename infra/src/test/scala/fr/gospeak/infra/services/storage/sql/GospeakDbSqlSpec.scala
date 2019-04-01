package fr.gospeak.infra.services.storage.sql

import fr.gospeak.infra.testingutils.RepoSpec
import fr.gospeak.libs.scalautils.domain._

class GospeakDbSqlSpec extends RepoSpec {
  describe("GospeakDbSql") {
    describe("insertMockData") {
      it("should not fail") {
        db.insertMockData().unsafeRunSync() shouldBe Done
      }
    }
  }
}
