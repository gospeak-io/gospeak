package gospeak.web

import gospeak.web.testingutils.BaseSpec

class GsCLISpec extends BaseSpec {
  describe("GsCLI") {
    describe("GenerateTables") {
      it("should generate the same tables") {
        GsCLI.GenerateTables.init()
        val existingFiles = GsCLI.GenerateTables.writer.readFiles().get
        val database = GsCLI.GenerateTables.reader.read().unsafeRunSync()
        val newFiles = GsCLI.GenerateTables.writer.generateFiles(database)
        newFiles.size shouldBe existingFiles.size
        newFiles.map { case (path, content) =>
          content.trim shouldBe existingFiles.getOrElse(path, "").trim
        }
      }
    }
  }
}
