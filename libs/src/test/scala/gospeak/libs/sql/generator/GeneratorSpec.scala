package gospeak.libs.sql.generator

import cats.data.NonEmptyList
import gospeak.libs.scala.FileUtils
import gospeak.libs.sql.generator.reader.H2Reader
import gospeak.libs.sql.generator.writer.ScalaWriter.{DatabaseConfig, FieldConfig, SchemaConfig, TableConfig}
import gospeak.libs.sql.generator.writer.{ScalaWriter, Writer}
import gospeak.libs.sql.testingutils.SqlSpec

class GeneratorSpec extends SqlSpec {
  private val reader = new H2Reader(
    schema = Some("PUBLIC"),
    excludes = Some(".*flyway.*"))
  private val writer = new ScalaWriter(
    directory = FileUtils.adaptLocalPath("libs/src/test/scala"),
    packageName = "gospeak.libs.sql.testingutils.database",
    identifierStrategy = Writer.IdentifierStrategy.upperCase,
    config = DatabaseConfig(
      scaladoc = _ => Some("Hello"),
      imports = List("gospeak.libs.sql.testingutils.Entities._"),
      schemas = Map("PUBLIC" -> SchemaConfig(tables = Map(
        "users" -> TableConfig(alias = Some("u"), fields = Map(
          "id" -> FieldConfig(customType = Some("User.Id")))),
        "categories" -> TableConfig(alias = "c", sort = TableConfig.Sort("name", NonEmptyList.of("-name", "id")), search = List("name"), fields = Map(
          "id" -> FieldConfig(customType = Some("Category.Id")))),
        "posts" -> TableConfig(alias = Some("p"), fields = Map(
          "id" -> FieldConfig(customType = Some("Post.Id"))))
      )))))

  describe("Generator") {
    ignore("should generate database tables") {
      Generator.generate(xa, reader, writer).unsafeRunSync()
    }
    it("should generate same files as before") {
      val existingFiles = writer.readFiles().get
      val database = reader.read(xa).unsafeRunSync()
      val newFiles = writer.generateFiles(database)
      newFiles.size shouldBe existingFiles.size
      newFiles.map { case (path, content) =>
        content.trim shouldBe existingFiles.getOrElse(path, "").trim
      }
    }
  }
}
