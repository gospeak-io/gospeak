package gospeak.libs.scala

import gospeak.libs.testingutils.BaseSpec

class FileUtilsSpec extends BaseSpec {
  private val path = FileUtils.adaptLocalPath("libs/target/test")
  private val filePath = path + "/test.txt"

  describe("FileUtils") {
    it("should write, read and delete a file") {
      val content = "aaa"
      FileUtils.write(filePath, content).get
      FileUtils.read(filePath).get shouldBe content
      FileUtils.deleteFile(filePath).get
      a[Exception] should be thrownBy FileUtils.read(filePath).get
    }
    it("should override an existing file") {
      val content1 = "aaa"
      val content2 = "bbb"

      FileUtils.write(filePath, content1).get
      FileUtils.read(filePath).get shouldBe content1

      FileUtils.write(filePath, content2).get
      FileUtils.read(filePath).get shouldBe content2

      FileUtils.deleteFile(filePath).get
    }
    it("should delete a non empty folder") {
      FileUtils.mkdirs(path + "/folder/non/empty").get
      FileUtils.write(path + "/folder/test.txt", "aaa").get
      FileUtils.exists(path + "/folder") shouldBe true
      FileUtils.delete(path + "/folder").get
      FileUtils.exists(path + "/folder") shouldBe false
    }
    it("should list files in folder") {
      FileUtils.mkdirs(path + "/src/main/scala/test").get
      FileUtils.mkdirs(path + "/test/main/scala/test").get
      FileUtils.write(path + "/readme.md", "aaa").get
      FileUtils.write(path + "/src/main/scala/test/main.scala", "aaa").get
      FileUtils.write(path + "/test/main/readme.md", "aaa").get
      FileUtils.listFiles(path, recursively = false).get shouldBe List(
        path + "/readme.md")
      FileUtils.listFiles(path).get shouldBe List(
        path + "/readme.md",
        path + "/src/main/scala/test/main.scala",
        path + "/test/main/readme.md")
    }
  }
}
