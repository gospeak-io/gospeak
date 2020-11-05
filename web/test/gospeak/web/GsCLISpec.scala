package gospeak.web

import gospeak.libs.scala.FileUtils
import gospeak.web.testingutils.BaseSpec

class GsCLISpec extends BaseSpec {
  describe("GsCLI") {
    describe("GenerateDatabaseTables") {
      it("should keep the generated database up to date") {
        val tmpFolder = "target/tests-cli"
        val w = GsCLI.GenerateDatabaseTables.writer
        GsCLI.GenerateDatabaseTables.run(folder = tmpFolder).unsafeRunSync()
        val currentDb = FileUtils.getDirContent(w.directory(tmpFolder).rootFolderPath).get
        val generatedDb = FileUtils.getDirContent(w.rootFolderPath).get
        generatedDb shouldBe currentDb

        FileUtils.delete(tmpFolder).get
      }
    }
  }
}
