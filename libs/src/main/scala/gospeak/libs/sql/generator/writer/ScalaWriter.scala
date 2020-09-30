package gospeak.libs.sql.generator.writer

import cats.data.NonEmptyList
import gospeak.libs.scala.StringUtils
import gospeak.libs.sql.generator.Database
import gospeak.libs.sql.generator.Database.{Field, FieldRef, Table}
import gospeak.libs.sql.generator.writer.ScalaWriter.{DatabaseConfig, TableConfig}
import gospeak.libs.sql.generator.writer.Writer.IdentifierStrategy

class ScalaWriter(directory: String,
                  packageName: String,
                  identifierStrategy: IdentifierStrategy = Writer.IdentifierStrategy.upperCase,
                  config: DatabaseConfig) extends Writer {
  require(config.getConfigErrors.isEmpty, s"DatabaseConfig has some errors :${config.getConfigErrors.map("\n - " + _).mkString}")

  override protected def getDatabaseErrors(db: Database): List[String] = config.getDatabaseErrors(db)

  override protected def rootFolderPath: String = directory + "/" + packageName.replaceAll("\\.", "/")

  override protected def tableFilePath(t: Table): String = tablesFolderPath + "/" + idf(t.name) + ".scala"

  /*
   * Template functions to generate the list of tables
   */

  override protected def listTablesFile(tables: List[Table]): String =
    s"""package $packageName
       |
       |import gospeak.libs.sql.dsl.Table.SqlTable
       |
       |${writeScaladoc(None)}
       |object Tables {
       |${tables.map(listTableAttr).map("  " + _).mkString("\n")}
       |
       |  def getTables: List[SqlTable] = List(${tables.map(t => idf(t.name)).mkString(", ")})
       |}
       |""".stripMargin

  protected def listTableAttr(t: Table): String = s"val ${idf(t.name)}: tables.${idf(t.name)} = tables.${idf(t.name)}.table"

  /*
   * Template functions to generate a Table class
   */

  override protected def tableFile(table: Table): String = {
    val tableConf = config.table(table)
    val tableName = idf(table.name)
    val alias = tableConf.alias.map(a => s"""Some("$a")""").getOrElse("None")
    val sorts = tableConf.sorts.map(tableSort).mkString(", ")
    val sortedFields = table.fields.zipWithIndex.map { case (f, i) => (f, config.field(f).index.getOrElse(i)) }.sortBy(_._2).map(_._1)
    val searchFields = tableConf.search.headOption.map(_ => tableConf.search.flatMap(f => table.fields.find(_.name == f))).getOrElse(sortedFields)
    s"""package $packageName.tables
       |
       |${tableImports(table)}
       |
       |${writeScaladoc(Some(table))}
       |class $tableName private(getAlias: Option[String] = $alias) extends Table.SqlTable("${table.schema}", "${table.name}", getAlias) {
       |  type Self = $tableName
       |
       |${sortedFields.map(tableFieldAttr(table, _)).map("  " + _).mkString("\n")}
       |
       |  override def getFields: List[SqlField[_, $tableName]] = List(${sortedFields.map(f => idf(f.name)).mkString(", ")})
       |
       |  override def getSorts: List[Sort] = List($sorts)
       |
       |  override def searchOn: List[SqlField[_, $tableName]] = List(${searchFields.map(f => idf(f.name)).mkString(", ")})
       |
       |  def alias(alias: String): $tableName = new $tableName(Some(alias))
       |}
       |
       |private[${packageName.split('.').last}] object $tableName {
       |  val table = new $tableName() // table instance, should be accessed through `$packageName.Tables` object
       |}
       |""".stripMargin
  }

  protected def tableImports(table: Table): String = {
    val tableConf = config.table(table)
    val scalaTypes = table.fields.map(scalaType).toSet
    val imports = List(
      Some("gospeak.libs.sql.dsl._"),
      Some("gospeak.libs.sql.dsl.Table._"),
      Some("cats.data.NonEmptyList").filter(_ => tableConf.sorts.nonEmpty),
      Some("java.time.Instant").filter(_ => scalaTypes.contains("Instant")),
      Some("java.time.LocalDate").filter(_ => scalaTypes.contains("LocalDate"))
    ).flatten ++ config.imports

    val groupedImports = imports.distinct.groupBy(_.split('.').dropRight(1).mkString(".")).map {
      case (_, List(singleImport)) => singleImport
      case (prefix, imports) =>
        val values = imports.map(_.split('.').last).sorted
        if (values.contains("_")) s"$prefix._" else s"$prefix.{${values.mkString(", ")}}"
    }.toList

    val jImports = groupedImports.filter(_.startsWith("java."))
    val sImports = groupedImports.filter(_.startsWith("scala."))
    val oImports = groupedImports.filterNot(i => i.startsWith("java.") || i.startsWith("scala."))

    List(
      jImports.sorted.map("import " + _).mkString("\n"),
      oImports.sorted.map("import " + _).mkString("\n"),
      sImports.sorted.map("import " + _).mkString("\n")
    ).filter(_.nonEmpty).mkString("\n\n")
  }

  protected def tableFieldAttr(t: Table, f: Field): String = {
    val (tableName, fieldName) = (idf(t.name), idf(f.name))
    val valueType = f.ref.filter(_ => config.customTypesFollowReferences).flatMap(config.field(_).customType)
      .orElse(config.field(f).customType)
      .getOrElse(scalaType(f))
    f.ref.map { r =>
      val fieldType = (if (f.nullable) "SqlFieldRefOpt" else "SqlFieldRef") + s"[$valueType, $tableName, ${idf(r.table)}]"
      val fieldRef = (if (r.schema == f.schema && r.table == f.table) "" else s"${idf(r.table)}.table.") + idf(r.field)
      s"""val $fieldName: $fieldType = new $fieldType(this, "${f.name}", $fieldRef) // ${f.`type`}"""
    }.getOrElse {
      val fieldType = (if (f.nullable) "SqlFieldOpt" else "SqlField") + s"[$valueType, $tableName]"
      s"""val $fieldName: $fieldType = new $fieldType(this, "${f.name}") // ${f.`type`}"""
    }
  }

  protected def tableSort(s: TableConfig.Sort): String = {
    def fieldOrder(f: TableConfig.Sort.Field): String = {
      f.expr.map(e => s"""Field.Order(${idf(f.name)}, asc = ${f.asc}, Some("$e"))""").getOrElse(idf(f.name) + "." + (if (f.asc) "asc" else "desc"))
    }

    s"""Sort("${s.slug}", "${s.label}", NonEmptyList.of(${s.fields.map(fieldOrder).toList.mkString(", ")}))"""
  }

  /*
   * Utils functions
   */

  protected def writeScaladoc(table: Option[Table]): String = {
    val userdoc = config.scaladoc(table)
      .map(_.trim)
      .filter(_.nonEmpty)
      .map(_.split('\n').map(l => if (l.trim.isEmpty) " *" else " * " + l).toList)
      .getOrElse(List())
      .mkString("\n")
    val tooldoc = s" * Class generated by ${getClass.getName}"
    val doc = List(userdoc, tooldoc).filter(_.nonEmpty).mkString("\n *\n")
    s"""/**
       |$doc
       | */""".stripMargin
  }

  protected def idf(value: String): String = identifierStrategy.format(value)

  protected def scalaType(f: Field): String = f.kind match {
    case "BIGINT" => "Long"
    case "BOOLEAN" => "Boolean"
    case "DATE" => "LocalDate"
    case "DOUBLE" => "Double"
    case "INTEGER" => "Int"
    case "SMALLINT" => "Short"
    case "TIMESTAMP" | "TIMESTAMP WITH TIME ZONE" => "Instant"
    case "VARCHAR" | "CHAR" => "String"
  }
}

object ScalaWriter {

  case class DatabaseConfig(scaladoc: Option[Table] => Option[String] = _ => None, // allow to add some scaladoc at the beginning of the files
                            imports: List[String] = List(), // imports to add on top of all Table files (for custom types for example)
                            customTypesFollowReferences: Boolean = true, // if a field has a reference to an other field having a custom type, it inherit from the custom type
                            schemas: Map[String, SchemaConfig] = Map()) {
    def schema(name: String): SchemaConfig = schemas.getOrElse(name, SchemaConfig())

    def table(schema: String, table: String): TableConfig = this.schema(schema).tables.getOrElse(table, TableConfig())

    def table(table: Table): TableConfig = this.table(table.schema, table.name)

    def field(schema: String, table: String, field: String): FieldConfig = this.table(schema, table).fields.getOrElse(field, FieldConfig())

    def field(table: Table, field: String): FieldConfig = this.field(table.schema, table.name, field)

    def field(field: Field): FieldConfig = this.field(field.schema, field.table, field.name)

    def field(field: FieldRef): FieldConfig = this.field(field.schema, field.table, field.field)

    def getConfigErrors: List[String] = {
      val duplicatedTableAlias = schemas
        .flatMap { case (schemaName, schemaConf) => schemaConf.tables.flatMap { case (tableName, tableConf) => tableConf.alias.map(a => (schemaName, tableName, a)) } }
        .groupBy(_._3).toList
        .collect { case (alias, tables) if tables.size > 1 => s"Alias '$alias' can't be used for multiple tables (${tables.map(t => s"${t._1}.${t._2}").mkString(", ")})" }

      duplicatedTableAlias
    }

    def getDatabaseErrors(db: Database): List[String] = {
      schemas.toList.flatMap { case (schemaName, schemaConf) =>
        db.schemas.find(_.name == schemaName).fold(List(s"Schema '$schemaName' declared in conf does not exist in Database")) { schema =>
          schemaConf.tables.toList.flatMap { case (tableName, tableConf) =>
            schema.tables.find(_.name == tableName).fold(List(s"Table '$schemaName.$tableName' declared in conf does not exist in Database")) { table =>
              val missingFields = tableConf.fields.toList.flatMap { case (fieldName, _) =>
                table.fields.find(_.name == fieldName).fold(List(s"Field '$schemaName.$tableName.$fieldName' declared in conf does not exist in Database"))(_ => List())
              }
              val missingSorts = tableConf.sorts.flatMap(sort => sort.fields.map(_.name).toList.flatMap(fieldName =>
                table.fields.find(_.name == fieldName).fold(List(s"Field '$fieldName' in sort '${sort.label}' of table '$schemaName.$tableName' does not exist in Database"))(_ => List())
              ))
              val missingSearch = tableConf.search.flatMap(fieldName =>
                table.fields.find(_.name == fieldName).fold(List(s"Field '$fieldName' in search of table '$schemaName.$tableName' does not exist in Database"))(_ => List())
              )
              missingFields ++ missingSorts ++ missingSearch
            }
          }
        }
      }
    }
  }

  case class SchemaConfig(tables: Map[String, TableConfig] = Map())

  case class TableConfig(alias: Option[String] = None, // table alias, should be unique
                         sorts: List[TableConfig.Sort] = List(), // available sorts for the table, first one is the default one
                         search: List[String] = List(), // fields to use on search, if not specified, all fields will be used
                         fields: Map[String, FieldConfig] = Map())

  object TableConfig {

    def apply(alias: String): TableConfig = new TableConfig(Some(alias), List())

    def apply(alias: String, sort: TableConfig.Sort): TableConfig = new TableConfig(Some(alias), List(sort))

    def apply(alias: String, sort: TableConfig.Sort, search: List[String]): TableConfig = new TableConfig(Some(alias), List(sort), search)

    def apply(alias: String, fields: Map[String, FieldConfig]): TableConfig = new TableConfig(Some(alias), List(), List(), fields)

    def apply(alias: String, sort: String, fields: Map[String, FieldConfig]): TableConfig = new TableConfig(Some(alias), List(TableConfig.Sort(sort)), List(), fields)

    def apply(alias: String, sort: String, search: List[String], fields: Map[String, FieldConfig]): TableConfig = new TableConfig(Some(alias), List(TableConfig.Sort(sort)), search, fields)

    def apply(alias: String, sort: TableConfig.Sort, fields: Map[String, FieldConfig]): TableConfig = new TableConfig(Some(alias), List(sort), List(), fields)

    def apply(alias: String, sort: TableConfig.Sort, search: List[String], fields: Map[String, FieldConfig]): TableConfig = new TableConfig(Some(alias), List(sort), search, fields)

    case class Sort(slug: String, label: String, fields: NonEmptyList[Sort.Field])

    object Sort {

      case class Field(name: String, asc: Boolean, expr: Option[String])

      object Field {
        def apply(value: String): Field = new Field(value.stripPrefix("-"), !value.startsWith("-"), None)

        def apply(value: String, expr: String): Field = new Field(value.stripPrefix("-"), !value.startsWith("-"), Some(expr))
      }

      def apply(field: String): Sort = new Sort(StringUtils.slugify(field), field, NonEmptyList.of(field).map(Field(_)))

      def apply(label: String, field: String): Sort = new Sort(StringUtils.slugify(label), label, NonEmptyList.of(field).map(Field(_)))

      def apply(slug: String, label: String, field: String): Sort = new Sort(StringUtils.slugify(slug), label, NonEmptyList.of(field).map(Field(_)))

      def apply(label: String, fields: NonEmptyList[String]): Sort = new Sort(StringUtils.slugify(label), label, fields.map(Field(_)))
    }

  }

  case class FieldConfig(index: Option[Int] = None, // override column index to reorder fields in the table
                         customType: Option[String] = None) // override the computed type with this one, should be the fully qualified name

  object FieldConfig {
    def apply(index: Int): FieldConfig = new FieldConfig(Some(index), None)

    def apply(customType: String): FieldConfig = new FieldConfig(None, Some(customType))

    def apply(index: Int, customType: String): FieldConfig = new FieldConfig(Some(index), Some(customType))
  }

}
