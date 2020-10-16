package gospeak.libs.sql.generator.writer

import gospeak.libs.sql.generator.Database
import gospeak.libs.sql.generator.writer.ScalaWriter.{DatabaseConfig, FieldConfig, SchemaConfig, TableConfig}
import gospeak.libs.testingutils.BaseSpec

class ScalaWriterSpec extends BaseSpec {
  private val db = Database(schemas = List(Database.Schema("PUBLIC", tables = List(
    Database.Table("PUBLIC", "users", fields = List(
      Database.Field("PUBLIC", "users", "id", 4, "INT", "INT NOT NULL", nullable = false, 1, None, None),
      Database.Field("PUBLIC", "users", "name", 12, "VARCHAR", "VARCHAR(50) NOT NULL", nullable = false, 2, None, None)
    ))))))

  describe("ScalaWriter") {
    describe("DatabaseConfig") {
      describe("getConfigErrors") {
        it("should forbid duplicated table alias") {
          DatabaseConfig(schemas = Map("PUBLIC" -> SchemaConfig(tables = Map(
            "users" -> TableConfig(alias = "u"),
            "conf" -> TableConfig(alias = "u")
          )))).getConfigErrors shouldBe List("Alias 'u' can't be used for multiple tables (PUBLIC.users, PUBLIC.conf)")
        }
      }
      describe("getDatabaseErrors") {
        it("should forbid schema not present in db") {
          DatabaseConfig(schemas = Map(
            "NotFound" -> SchemaConfig()
          )).getDatabaseErrors(db) shouldBe List("Schema 'NotFound' declared in conf does not exist in Database")
        }
        it("should forbid table not present in db") {
          DatabaseConfig(schemas = Map("PUBLIC" -> SchemaConfig(tables = Map(
            "NotFound" -> TableConfig()
          )))).getDatabaseErrors(db) shouldBe List("Table 'PUBLIC.NotFound' declared in conf does not exist in Database")
        }
        it("should forbid field not present in db") {
          DatabaseConfig(schemas = Map("PUBLIC" -> SchemaConfig(tables = Map(
            "users" -> TableConfig(fields = Map(
              "NotFound" -> FieldConfig()
            )))))).getDatabaseErrors(db) shouldBe List("Field 'PUBLIC.users.NotFound' declared in conf does not exist in Database")
        }
        it("should forbid table sort using field not present in db") {
          DatabaseConfig(schemas = Map("PUBLIC" -> SchemaConfig(tables = Map(
            "users" -> TableConfig(sorts = List(TableConfig.Sort("name", "NotFound")))
          )))).getDatabaseErrors(db) shouldBe List("Field 'NotFound' in sort 'name' of table 'PUBLIC.users' does not exist in Database")
        }
        it("should forbid table search using field not present in db") {
          DatabaseConfig(schemas = Map("PUBLIC" -> SchemaConfig(tables = Map(
            "users" -> TableConfig(search = List("NotFound"))
          )))).getDatabaseErrors(db) shouldBe List("Field 'NotFound' in search of table 'PUBLIC.users' does not exist in Database")
        }
      }
    }
  }
}
