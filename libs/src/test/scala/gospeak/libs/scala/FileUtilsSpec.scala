package gospeak.libs.scala

import gospeak.libs.testingutils.BaseSpec
import org.scalatest.BeforeAndAfterEach

class FileUtilsSpec extends BaseSpec with BeforeAndAfterEach {
  private val root = "target/tests-file-utils"
  private val filePath = s"$root/test.txt"

  override protected def beforeEach(): Unit = FileUtils.mkdirs(root).get

  override protected def afterEach(): Unit = FileUtils.delete(root).get

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
      FileUtils.mkdirs(s"$root/folder/non/empty").get
      FileUtils.write(s"$root/folder/test.txt", "aaa").get
      FileUtils.exists(s"$root/folder") shouldBe true
      FileUtils.delete(s"$root/folder").get
      FileUtils.exists(s"$root/folder") shouldBe false
    }
    it("should list files in folder") {
      FileUtils.mkdirs(s"$root/src/main/scala/test").get
      FileUtils.mkdirs(s"$root/test/main/scala/test").get
      FileUtils.write(s"$root/readme.md", "aaa").get
      FileUtils.write(s"$root/src/main/scala/test/main.scala", "aaa").get
      FileUtils.write(s"$root/test/main/readme.md", "aaa").get
      FileUtils.listFiles(root, recursively = false).get shouldBe List(
        s"$root/readme.md")
      FileUtils.listFiles(root).get shouldBe List(
        s"$root/readme.md",
        s"$root/src/main/scala/test/main.scala",
        s"$root/test/main/readme.md")
    }
    it("should read a file with correct line breaks") {
      val noEmptyEndLine =
        """Bonjour,
          |ça va ?""".stripMargin
      FileUtils.write(filePath, noEmptyEndLine).get
      FileUtils.read(filePath).get shouldBe noEmptyEndLine

      val emptyEndLine =
        """Bonjour,
          |ça va ?
          |""".stripMargin
      FileUtils.write(filePath, emptyEndLine).get
      FileUtils.read(filePath).get shouldBe emptyEndLine

      val manyEmptyEndLines =
        """Bonjour,
          |
          |""".stripMargin
      FileUtils.write(filePath, manyEmptyEndLines).get
      FileUtils.read(filePath).get shouldBe manyEmptyEndLines
    }
    it("should list folder content") {
      FileUtils.mkdirs(s"$root/src/main/scala/fr/loicknuchel").get
      FileUtils.write(s"$root/src/main/scala/fr/loicknuchel/Main.scala", "public class Main").get
      FileUtils.write(s"$root/src/main/scala/README.md", "The readme").get

      FileUtils.getDirContent(s"$root/src/main/scala").get shouldBe Map(
        "README.md" -> "The readme",
        "fr/loicknuchel/Main.scala" -> "public class Main")
    }
  }
}
