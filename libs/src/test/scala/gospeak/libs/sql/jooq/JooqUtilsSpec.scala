package gospeak.libs.sql.jooq

import doobie.implicits._
import gospeak.libs.sql.testingutils.Entities.Kind
import gospeak.libs.sql.testingutils.SqlSpec
import org.jooq.SQLDialect
import org.jooq.impl.DSL._

class JooqUtilsSpec extends SqlSpec {
  private val jooqCtx = using(SQLDialect.H2)

  describe("JooqUtils") {
    it("should convert a jOOQ query to Doobie fragment") {
      val query = jooqCtx
        .select(field("char"), field("varchar"), field("timestamp"), field("date"), field("boolean"), field("int"), field("bigint"), field("double"))
        .from(table("kinds"))
        .where(field("char").equal(Kind.one.char)
          .and(field("varchar").equal(Kind.one.varchar))
          .and(field("timestamp").equal(Kind.one.timestamp))
          .and(field("date").equal(Kind.one.date))
          .and(field("boolean").equal(Boolean.box(Kind.one.boolean)))
          .and(field("int").equal(Int.box(Kind.one.int)))
          .and(field("bigint").equal(Long.box(Kind.one.bigint)))
          .and(field("double").equal(Double.box(Kind.one.double))))
      val res = JooqUtils.queryToFragment(query)
        .query[Kind]
        .option
        .transact(xa)
        .unsafeRunSync()
      res shouldBe Some(Kind.one)
    }
    ignore("should generate classes for the database") {
      JooqUtils.generateTables(
        driver = dbDriver,
        url = dbUrl,
        directory = "libs/src/main/scala",
        packageName = "gospeak.libs.sql.jooqdb")
    }
  }
}
