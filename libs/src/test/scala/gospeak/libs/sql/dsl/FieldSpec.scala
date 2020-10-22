package gospeak.libs.sql.dsl

import gospeak.libs.sql.testingutils.database.Tables.{POSTS, USERS}
import gospeak.libs.testingutils.BaseSpec

class FieldSpec extends BaseSpec {
  private val q = USERS.select.fields(USERS.NAME).option[String]

  describe("Field") {
    it("should generate sql for SqlField") {
      val f1 = new SqlField(POSTS, "title", POSTS.TITLE.info, None)
      f1.fr.query.sql shouldBe "p.title"
      f1.value.query.sql shouldBe "p.title"
      f1.ref.query.sql shouldBe "p.title"
      val f2 = new SqlField(POSTS, "title", POSTS.TITLE.info, Some("t"))
      f2.fr.query.sql shouldBe "p.title as t"
      f2.value.query.sql shouldBe "p.title"
      f2.ref.query.sql shouldBe "p.title"
    }
    it("should generate sql for SqlFieldRef") {
      val f1 = new SqlFieldRef(POSTS, "author", POSTS.AUTHOR.info, None, USERS.ID)
      f1.fr.query.sql shouldBe "p.author"
      f1.value.query.sql shouldBe "p.author"
      f1.ref.query.sql shouldBe "p.author"
      val f2 = new SqlFieldRef(POSTS, "author", POSTS.AUTHOR.info, Some("a"), USERS.ID)
      f2.fr.query.sql shouldBe "p.author as a"
      f2.value.query.sql shouldBe "p.author"
      f2.ref.query.sql shouldBe "p.author"
    }
    it("should generate sql for TableField") {
      val f1 = TableField("name", Some("u"), Some("n"))
      f1.fr.query.sql shouldBe "u.name as n"
      f1.value.query.sql shouldBe "u.name"
      f1.ref.query.sql shouldBe "n"
      val f2 = TableField("name", Some("u"), None)
      f2.fr.query.sql shouldBe "u.name"
      f2.value.query.sql shouldBe "u.name"
      f2.ref.query.sql shouldBe "u.name"
      val f3 = TableField("name", None, Some("n"))
      f3.fr.query.sql shouldBe "name as n"
      f3.value.query.sql shouldBe "name"
      f3.ref.query.sql shouldBe "n"
      val f4 = TableField("name", None, None)
      f4.fr.query.sql shouldBe "name"
      f4.value.query.sql shouldBe "name"
      f4.ref.query.sql shouldBe "name"
    }
    it("should generate sql for NullField") {
      val f1 = NullField("name")
      f1.fr.query.sql shouldBe "null as name"
      f1.value.query.sql shouldBe "null"
      f1.ref.query.sql shouldBe "name"
    }
    it("should generate sql for QueryField") {
      val f1 = QueryField(q, Some("n"))
      f1.fr.query.sql shouldBe "(SELECT u.name FROM users u) as n"
      f1.value.query.sql shouldBe "(SELECT u.name FROM users u)"
      f1.ref.query.sql shouldBe "n"
      val f2 = QueryField(q, None)
      f2.fr.query.sql shouldBe "(SELECT u.name FROM users u)"
      f2.value.query.sql shouldBe "(SELECT u.name FROM users u)"
      f2.ref.query.sql shouldBe "SELECT u.name FROM users u"
    }
    it("should generate sql for SimpleAggField") {
      val f1 = SimpleAggField("name", Some("n"))
      f1.fr.query.sql shouldBe "name as n"
      f1.value.query.sql shouldBe "name"
      f1.ref.query.sql shouldBe "n"
      val f2 = SimpleAggField("name", None)
      f2.fr.query.sql shouldBe "name"
      f2.value.query.sql shouldBe "name"
      f2.ref.query.sql shouldBe "name"
    }
    it("should generate sql for QueryAggField") {
      val f1 = QueryAggField(q, Some("n"))
      f1.fr.query.sql shouldBe "(SELECT u.name FROM users u) as n"
      f1.value.query.sql shouldBe "(SELECT u.name FROM users u)"
      f1.ref.query.sql shouldBe "n"
      val f2 = QueryAggField(q, None)
      f2.fr.query.sql shouldBe "(SELECT u.name FROM users u)"
      f2.value.query.sql shouldBe "(SELECT u.name FROM users u)"
      f2.ref.query.sql shouldBe "SELECT u.name FROM users u"
    }
    describe("Order") {
      it("should build order with nulls last") {
        Field.Order(TableField("name"), asc = true, None).fr(nullsFirst = false).query.sql shouldBe "name IS NULL, name"
        // Field.Order(TableField("null").as("name"), asc = true, None).fr(nullsFirst = false).query.sql shouldBe "name IS NULL, name"
        Field.Order(TableField("name"), asc = false, None).fr(nullsFirst = false).query.sql shouldBe "name IS NULL, name DESC"
      }
      it("should build order with nulls first") {
        Field.Order(TableField("name"), asc = true, None).fr(nullsFirst = true).query.sql shouldBe "name IS NOT NULL, name"
      }
    }
  }
}
