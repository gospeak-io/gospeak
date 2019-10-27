package fr.gospeak.infra.services.storage.sql

import fr.gospeak.infra.services.storage.sql.testingutils.RepoSpec
import fr.gospeak.infra.testingutils.Values
import fr.gospeak.libs.scalautils.domain.Done

class GospeakDbSqlSpec extends RepoSpec {
  describe("GospeakDbSql") {
    describe("insertMockData") {
      it("should not fail") {
        db.insertMockData(Values.gsConf).unsafeRunSync() shouldBe Done
      }
    }
  }
}
