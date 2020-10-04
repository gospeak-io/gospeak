package gospeak.libs.sql.generator.reader

import cats.effect.IO
import doobie.syntax.connectionio._
import doobie.syntax.string._
import gospeak.libs.sql.generator.Database
import gospeak.libs.sql.generator.reader.H2Reader._

class H2Reader(schema: Option[String] = None,
               excludes: Option[String] = None) extends Reader {
  override def read(xa: doobie.Transactor[IO]): IO[Database] = for {
    columns <- readColumns(xa)
    crossReferences <- readCrossReferences(xa)
  } yield buildDatabase(columns, crossReferences)

  protected def buildDatabase(columns: List[Column], crossReferences: List[CrossReference]): Database = {
    val refs = crossReferences.map(r => (Database.FieldRef(r.FKTABLE_SCHEMA, r.FKTABLE_NAME, r.FKCOLUMN_NAME), Database.FieldRef(r.PKTABLE_SCHEMA, r.PKTABLE_NAME, r.PKCOLUMN_NAME))).toMap
    Database(columns.groupBy(_.TABLE_SCHEMA).toList.sortBy(_._1).map { case (schema, tables) =>
      Database.Schema(schema, tables.groupBy(_.TABLE_NAME).toList.sortBy(_._1).map { case (table, columns) =>
        Database.Table(schema, table, columns.filter(_.IS_VISIBLE).sortBy(_.ORDINAL_POSITION).map { column =>
          val ref = Database.FieldRef(schema, table, column.COLUMN_NAME)
          Database.Field(schema, table, column.COLUMN_NAME, column.TYPE_NAME, column.COLUMN_TYPE, column.IS_NULLABLE, column.COLUMN_DEFAULT, refs.get(ref))
        }.filterNot(f => excludes.forall(e => f.name.matches(e))))
      }.filterNot(t => excludes.forall(e => t.name.matches(e))))
    }.filterNot(s => excludes.forall(e => s.name.matches(e))))
  }

  protected def readColumns(xa: doobie.Transactor[IO]): IO[List[Column]] =
    (fr0"SELECT TABLE_CATALOG, TABLE_SCHEMA, TABLE_NAME, COLUMN_NAME, ORDINAL_POSITION, DOMAIN_CATALOG, DOMAIN_SCHEMA, DOMAIN_NAME, COLUMN_DEFAULT, IS_NULLABLE, DATA_TYPE, CHARACTER_MAXIMUM_LENGTH, CHARACTER_OCTET_LENGTH, NUMERIC_PRECISION, NUMERIC_PRECISION_RADIX, NUMERIC_SCALE, DATETIME_PRECISION, INTERVAL_TYPE, INTERVAL_PRECISION, CHARACTER_SET_NAME, COLLATION_NAME, TYPE_NAME, NULLABLE, IS_COMPUTED, SELECTIVITY, CHECK_CONSTRAINT, SEQUENCE_NAME, REMARKS, SOURCE_DATA_TYPE, COLUMN_TYPE, COLUMN_ON_UPDATE, IS_VISIBLE FROM INFORMATION_SCHEMA.COLUMNS" ++
      schema.map(s => fr0" WHERE TABLE_SCHEMA=$s").getOrElse(fr0"")).query[Column].to[List].transact(xa)

  def readConstraints(xa: doobie.Transactor[IO]): IO[List[Constraint]] =
    (fr0"SELECT CONSTRAINT_CATALOG, CONSTRAINT_SCHEMA, CONSTRAINT_NAME, CONSTRAINT_TYPE, TABLE_CATALOG, TABLE_SCHEMA, TABLE_NAME, UNIQUE_INDEX_NAME, CHECK_EXPRESSION, COLUMN_LIST, REMARKS, SQL, ID FROM INFORMATION_SCHEMA.CONSTRAINTS" ++
      schema.map(s => fr0" WHERE TABLE_SCHEMA=$s").getOrElse(fr0"")).query[Constraint].to[List].transact(xa)

  def readCrossReferences(xa: doobie.Transactor[IO]): IO[List[CrossReference]] =
    (fr0"SELECT PKTABLE_CATALOG, PKTABLE_SCHEMA, PKTABLE_NAME, PKCOLUMN_NAME, FKTABLE_CATALOG, FKTABLE_SCHEMA, FKTABLE_NAME, FKCOLUMN_NAME, ORDINAL_POSITION, UPDATE_RULE, DELETE_RULE, FK_NAME, PK_NAME, DEFERRABILITY FROM INFORMATION_SCHEMA.CROSS_REFERENCES" ++
      schema.map(s => fr0" WHERE PKTABLE_SCHEMA=$s").getOrElse(fr0"")).query[CrossReference].to[List].transact(xa)

  def readTables(xa: doobie.Transactor[IO]): IO[List[Table]] =
    (fr0"SELECT TABLE_CATALOG, TABLE_SCHEMA, TABLE_NAME, TABLE_TYPE, STORAGE_TYPE, SQL, REMARKS, LAST_MODIFICATION, ID, TYPE_NAME, TABLE_CLASS, ROW_COUNT_ESTIMATE FROM INFORMATION_SCHEMA.TABLES" ++
      schema.map(s => fr0" WHERE TABLE_SCHEMA=$s").getOrElse(fr0"")).query[Table].to[List].transact(xa)
}

object H2Reader {

  case class Column(TABLE_CATALOG: String, // ex: "4b0c3610-0d2e-493c-9895-9d004fa7bab9"
                    TABLE_SCHEMA: String, // ex: "INFORMATION_SCHEMA", "PUBLIC"
                    TABLE_NAME: String, // ex: "users", "posts"
                    COLUMN_NAME: String, // ex: "id", "title"
                    ORDINAL_POSITION: Int, // ex: 1, 2, 3
                    DOMAIN_CATALOG: Option[String],
                    DOMAIN_SCHEMA: Option[String],
                    DOMAIN_NAME: Option[String],
                    COLUMN_DEFAULT: Option[String], // ex: "CURRENT_TIMESTAMP"
                    IS_NULLABLE: Boolean, // ex: "YES", "NO"
                    DATA_TYPE: Int, // ex: 12, 4, 5, -5, 16, 8, 2014, 93, 1, 91
                    CHARACTER_MAXIMUM_LENGTH: Int, // ex: 2147483647, 10, 5, 19, 1, 17, 35, 50, 200, 20, 1000, 100, 26, 4, 4096
                    CHARACTER_OCTET_LENGTH: Int, // ex: 2147483647, 10, 5, 19, 1, 17, 35, 50, 200, 20, 1000, 100, 26, 4, 4096
                    NUMERIC_PRECISION: Int, // ex: 2147483647, 10, 5, 19, 1, 17, 35, 50, 200, 20, 1000, 100, 26, 4, 4096
                    NUMERIC_PRECISION_RADIX: Int, // ex: 10
                    NUMERIC_SCALE: Int, // ex: 0, 6, 9
                    DATETIME_PRECISION: Option[Int], // ex: 0, 6, 9
                    INTERVAL_TYPE: Option[String],
                    INTERVAL_PRECISION: Option[String],
                    CHARACTER_SET_NAME: String, // ex: "Unicode"
                    COLLATION_NAME: String, // ex: "OFF"
                    TYPE_NAME: String, // ex: "BIGINT", "BOOLEAN", "CHAR", "DATE", "DOUBLE", "INTEGER", "TIMESTAMP", "VARCHAR"
                    NULLABLE: Boolean, // ex: 1, 0
                    IS_COMPUTED: Boolean, // ex: "FALSE"
                    SELECTIVITY: Int, // ex: 50
                    CHECK_CONSTRAINT: String, // ex: ""
                    SEQUENCE_NAME: Option[String],
                    REMARKS: String, // ex: ""
                    SOURCE_DATA_TYPE: Option[String],
                    COLUMN_TYPE: String, // ex: "BIGINT", "BOOLEAN NOT NULL", "BOOLEAN", "CHAR(4)", "DATE", "DOUBLE PRECISION", "INT NOT NULL", "INT", "TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL", "TIMESTAMP NOT NULL", "TIMESTAMP", "VARCHAR(10) NOT NULL", "VARCHAR(50)"
                    COLUMN_ON_UPDATE: Option[String],
                    IS_VISIBLE: Boolean) // ex: "TRUE"

  case class Constraint(CONSTRAINT_CATALOG: String, // ex: "7c0d52f8-4087-47a8-a2b0-5e519408257d"
                        CONSTRAINT_SCHEMA: String, // ex: "PUBLIC"
                        CONSTRAINT_NAME: String, // ex: "CONSTRAINT_4", "CONSTRAINT_6", "CONSTRAINT_65", "CONSTRAINT_65E", "CONSTRAINT_65E7", "flyway_schema_history_pk"
                        CONSTRAINT_TYPE: String, // ex: "PRIMARY KEY", "REFERENTIAL"
                        TABLE_CATALOG: String, // ex: "6dba21bc-ad48-4a25-bd3d-d653b1a7bb7a"
                        TABLE_SCHEMA: String, // ex: "PUBLIC"
                        TABLE_NAME: String, // ex: "categories", "flyway_schema_history", "posts", "users"
                        UNIQUE_INDEX_NAME: String, // ex: "PRIMARY_KEY_4", "PRIMARY_KEY_6", "PRIMARY_KEY_65", "PRIMARY_KEY_6A"
                        CHECK_EXPRESSION: Option[String], // ex:
                        COLUMN_LIST: String, // ex: "author", "category", "id", "installed_rank"
                        REMARKS: String, // ex: ""
                        SQL: String, // ex: 'ALTER TABLE "PUBLIC"."categories" ADD CONSTRAINT "PUBLIC"."CONSTRAINT_4" PRIMARY KEY("id") INDEX "PUBLIC"."PRIMARY_KEY_4"', 'ALTER TABLE "PUBLIC"."posts" ADD CONSTRAINT "PUBLIC"."CONSTRAINT_65E" FOREIGN KEY("author") INDEX "PUBLIC"."CONSTRAINT_INDEX_6" REFERENCES "PUBLIC"."users"("id") NOCHECK'
                        ID: Int) // ex: 10, 13, 16, 18, 20, 6

  case class CrossReference(PKTABLE_CATALOG: String, // ex: "543acadc-3ae0-4e29-93c3-58d7a5b9e524"
                            PKTABLE_SCHEMA: String, // ex: "PUBLIC"
                            PKTABLE_NAME: String, // ex: "categories", "users"
                            PKCOLUMN_NAME: String, // ex: "id"
                            FKTABLE_CATALOG: String, // ex: "543acadc-3ae0-4e29-93c3-58d7a5b9e524"
                            FKTABLE_SCHEMA: String, // ex: "PUBLIC"
                            FKTABLE_NAME: String, // ex: "posts"
                            FKCOLUMN_NAME: String, // ex: "author", "category"
                            ORDINAL_POSITION: Int, // ex: 1
                            UPDATE_RULE: Int, // ex: 1
                            DELETE_RULE: Int, // ex: 1
                            FK_NAME: String, // ex: "CONSTRAINT_65E", "CONSTRAINT_65E7"
                            PK_NAME: String, // ex: "PRIMARY_KEY_4", "PRIMARY_KEY_6A"
                            DEFERRABILITY: Int) // ex: 7

  case class Table(TABLE_CATALOG: String, // ex: "649fa3e4-b42b-44eb-9696-84106b29b9ed"
                   TABLE_SCHEMA: String, // ex: "INFORMATION_SCHEMA", "PUBLIC"
                   TABLE_NAME: String, // ex: "users", "posts"
                   TABLE_TYPE: String, // ex: "SYSTEM TABLE", "TABLE"
                   STORAGE_TYPE: String, // ex: "CACHED", "MEMORY"
                   SQL: Option[String], // ex: 'CREATE MEMORY TABLE "PUBLIC"."categories"("id" INT NOT NULL, "name" VARCHAR(50) NOT NULL)'
                   REMARKS: String, // ex: ""
                   LAST_MODIFICATION: Long, // ex: 102, 107, 108, 116, 71, 81, 9223372036854775807
                   ID: Int, // ex: -1, -10, 11, 14, 21, 4, 8
                   TYPE_NAME: Option[String],
                   TABLE_CLASS: String, // ex: "org.h2.mvstore.db.MVTable, org.h2.table.MetaTable"
                   ROW_COUNT_ESTIMATE: Int) // ex: 1, 1000, 2, 3
}
