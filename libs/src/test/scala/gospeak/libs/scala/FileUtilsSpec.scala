package gospeak.libs.scala

import org.scalatest.{FunSpec, Matchers}

class FileUtilsSpec extends FunSpec with Matchers {
  private val path = if (FileUtils.exists("libs")) "libs/target/test/test.txt" else "target/test/test.txt"

  describe("FileUtils") {
    it("should write, read and delete a file") {
      val content = "aaa"
      FileUtils.write(path, content).get
      FileUtils.read(path).get shouldBe content
      FileUtils.delete(path).get
      a[Exception] should be thrownBy FileUtils.read(path).get
    }
    it("should override an existing file") {
      val content1 = "aaa"
      val content2 = "bbb"

      FileUtils.write(path, content1).get
      FileUtils.read(path).get shouldBe content1

      FileUtils.write(path, content2).get
      FileUtils.read(path).get shouldBe content2

      FileUtils.delete(path).get
    }
  }
}
