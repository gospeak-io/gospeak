package gospeak.libs.sql.generator

case class Database(schemas: List[Database.Schema])

object Database {

  case class Schema(name: String,
                    tables: List[Table])

  case class Table(schema: String,
                   name: String,
                   fields: List[Field])

  case class Field(schema: String,
                   table: String,
                   name: String,
                   kind: String,
                   `type`: String,
                   nullable: Boolean,
                   defaultValue: Option[String],
                   ref: Option[FieldRef]) // assume foreign keys have only one field and link to only one table

  case class FieldRef(schema: String, table: String, field: String)

}
