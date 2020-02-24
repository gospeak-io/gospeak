package gospeak.infra.services.storage.sql

import gospeak.infra.services.storage.sql.testingutils.RepoSpec
import gospeak.infra.testingutils.Values
import gospeak.libs.scala.domain.Done

class GsRepoSqlSpec extends RepoSpec {
  describe("GospeakDbSql") {
    describe("insertMockData") {
      it("should not fail") {
        db.insertMockData().unsafeRunSync() shouldBe Done
      }
    }
  }
}
