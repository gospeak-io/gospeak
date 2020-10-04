package gospeak.libs.sql.jooq

import java.time.{Instant, LocalDate}

import doobie.syntax.connectionio._
import doobie.util.meta.Meta
import gospeak.libs.sql.testingutils.Entities.Kind
import gospeak.libs.sql.testingutils.SqlSpec
import gospeak.libs.sql.testingutils.jooqdb.Tables._
import org.jooq.SQLDialect
import org.jooq.impl.DSL._

class JooqUtilsSpec extends SqlSpec {
  private val jooqCtx = using(SQLDialect.H2)
  private implicit val instantMeta: Meta[Instant] = doobie.implicits.legacy.instant.JavaTimeInstantMeta
  private implicit val localDateMeta: Meta[LocalDate] = doobie.implicits.legacy.localdate.JavaTimeLocalDateMeta

  describe("JooqUtils") {
    ignore("should generate classes for the database") {
      JooqUtils.generateTables(
        driver = dbDriver,
        url = dbUrl,
        directory = "libs/src/test/scala",
        packageName = "gospeak.libs.sql.testingutils.jooqdb")
    }
    it("should convert a jOOQ query to Doobie fragment") {
      val query = jooqCtx
        .select(field("char"), field("varchar"), field("timestamp"), field("date"), field("boolean"), field("int"), field("bigint"), field("double"), field("a_long_name"))
        .from(table("kinds"))
        .where(field("char").equal(Kind.one.char)
          .and(field("varchar").equal(Kind.one.varchar))
          .and(field("timestamp").equal(Kind.one.timestamp))
          .and(field("date").equal(Kind.one.date))
          .and(field("boolean").equal(Boolean.box(Kind.one.boolean)))
          .and(field("int").equal(Int.box(Kind.one.int)))
          .and(field("bigint").equal(Long.box(Kind.one.bigint)))
          .and(field("double").equal(Double.box(Kind.one.double)))
          .and(field("a_long_name").equal(Int.box(Kind.one.a_long_name))))
      val fr = JooqUtils.queryToFragment(query)
      fr.query[Kind].sql shouldBe "select char, varchar, timestamp, date, boolean, int, bigint, double, a_long_name " +
        "from kinds where " +
        "(char = cast(? as varchar) and " +
        "varchar = cast(? as varchar) and " +
        "timestamp = cast(? as timestamp with time zone) and " +
        "date = cast(? as date) and " +
        "boolean = cast(? as boolean) and " +
        "int = cast(? as int) and " +
        "bigint = cast(? as bigint) and " +
        "double = cast(? as double) and " +
        "a_long_name = cast(? as int))"
      val res = fr.query[Kind]
        .option
        .transact(xa)
        .unsafeRunSync()
      res shouldBe Some(Kind.one)
    }
    it("should generate insert") {
      val query = jooqCtx.insertInto(USERS).columns(USERS.ID, USERS.NAME, USERS.EMAIL).values(4, "Lou", "lou@mail.com")
      val fr = JooqUtils.queryToFragment(query)
      fr.update.sql shouldBe "insert into \"PUBLIC\".\"users\" (\"id\", \"name\", \"email\") values (cast(? as int), cast(? as varchar), cast(? as varchar))"
      val res = fr.update.run.transact(xa).unsafeRunSync()
      res shouldBe 1
    }
  }
}
