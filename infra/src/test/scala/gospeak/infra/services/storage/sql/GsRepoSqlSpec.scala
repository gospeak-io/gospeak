package gospeak.infra.services.storage.sql

import gospeak.infra.services.storage.sql.testingutils.RepoSpec
import gospeak.libs.scala.domain.Done

class GsRepoSqlSpec extends RepoSpec {
  describe("GsRepoSql") {
    it("should insert mock data without failing") {
      db.insertMockData().unsafeRunSync() shouldBe Done
    }
  }
}
