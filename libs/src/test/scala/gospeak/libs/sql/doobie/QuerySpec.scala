package gospeak.libs.sql.doobie

import cats.data.NonEmptyList
import doobie.implicits._
import gospeak.libs.scala.domain.Page
import gospeak.libs.sql.doobie.Query._
import gospeak.libs.sql.doobie.QuerySpec.Entity
import gospeak.libs.testingutils.BaseSpec

class QuerySpec extends BaseSpec {
  describe("Query") {
    describe("Insert") {
      it("should build an insert") {
        val entity = Entity(1, "n")
        val sql = Insert[Entity]("table", List(Field("id", "t"), Field("name", "t")), entity, e => fr0"${e.id}, ${e.name}").fr.update.sql
        sql shouldBe "INSERT INTO table (id, name) VALUES (?, ?)"
      }
    }
    describe("Update") {
      it("should build an update") {
        val e = Entity(1, "n")
        val sql = Update(fr0"table t", fr0"t.name=${e.name}", fr0" WHERE t.id=${e.id}").fr.update.sql
        sql shouldBe "UPDATE table t SET t.name=? WHERE t.id=?"
      }
    }
    describe("Delete") {
      it("should build a delete") {
        val e = Entity(1, "n")
        val sql = Delete(fr0"table t", fr0" WHERE t.id=${e.id}").fr.update.sql
        sql shouldBe "DELETE FROM table t WHERE t.id=?"
      }
    }
    describe("Select") {
      it("should build a select") {
        val e = Entity(1, "n")
        val sql = Select(fr0"table t", List(Field("id", "t"), Field("name", "t")), List(), List(), Some(fr0" WHERE t.id=${e.id}"), Table.Sorts("name", "t"), None, Some(3)).fr.update.sql
        sql shouldBe "SELECT t.id, t.name FROM table t WHERE t.id=? ORDER BY t.name IS NULL, t.name LIMIT 3"
      }
    }
    describe("SelectPage") {
      it("should build a select") {
        val e = Entity(1, "n")
        // val sql = SelectPage("table t", List(Field("id", "t"), Field("name", "t")), Some(fr0"WHERE t.id=${e.id}")).fr.update.sql
        // sql shouldBe "SELECT t.id, t.name FROM table t WHERE t.id=?"
      }
    }
    describe("whereFragment") {
      it("should build the where fragment") {
        val e = Entity(1, "n")
        val fields = List(Field("name", "t"), Field("desc", "t"))
        val where = fr0"WHERE t.id=${e.id}"
        whereFragment(None, None, fields) shouldBe None
        whereFragment(Some(where), None, fields).get.query.sql shouldBe "WHERE t.id=?"
        whereFragment(None, Some(Page.Search("q")), fields).get.query.sql shouldBe " WHERE t.name ILIKE ? OR t.desc ILIKE ?"
        whereFragment(Some(where), Some(Page.Search("q")), fields).get.query.sql shouldBe "WHERE t.id=? AND (t.name ILIKE ? OR t.desc ILIKE ?)"
      }
    }
    describe("orderByFragment") {
      it("should build the orderBy fragment") {
        orderByFragment(NonEmptyList.of(Field("name", ""))).query.sql shouldBe " ORDER BY name IS NULL, name"
        orderByFragment(NonEmptyList.of(Field("-name", ""))).query.sql shouldBe " ORDER BY name IS NULL, name DESC"
        orderByFragment(NonEmptyList.of(Field("name", "")), nullsFirst = true).query.sql shouldBe " ORDER BY name IS NOT NULL, name"
        orderByFragment(NonEmptyList.of(Field("name", "t"))).query.sql shouldBe " ORDER BY t.name IS NULL, t.name"
        orderByFragment(NonEmptyList.of(Field("name", ""), Field("date", ""))).query.sql shouldBe " ORDER BY name IS NULL, name, date IS NULL, date"
      }
    }
    describe("limitFragment") {
      it("should build the limit fragment") {
        limitFragment(Page.Size(20), Page.Offset(12)).query.sql shouldBe " LIMIT 20 OFFSET 12"
      }
    }
    describe("paginationFragment") {
      val e = Entity(1, "n")
      val where = fr0"WHERE t.id=${e.id}"
      val p = Page.Params.defaults
      val fields = List(Field("name", "t"), Field("desc", "t"))
      val sorts = Table.Sorts("created", "t")
      val prefix = "t"
      it("should build pagination") {
        val (w, o, l) = paginationFragment(prefix, None, p, sorts, fields)
        (w ++ o ++ l).query.sql shouldBe " ORDER BY t.created IS NULL, t.created LIMIT 20 OFFSET 0"
      }
      it("should build pagination with where") {
        val (w, o, l) = paginationFragment(prefix, Some(where), p, sorts, fields)
        (w ++ o ++ l).query.sql shouldBe "WHERE t.id=? ORDER BY t.created IS NULL, t.created LIMIT 20 OFFSET 0"
      }
      it("should build pagination with search") {
        val (w, o, l) = paginationFragment(prefix, None, p.search("q"), sorts, fields)
        (w ++ o ++ l).query.sql shouldBe " WHERE t.name ILIKE ? OR t.desc ILIKE ? ORDER BY t.created IS NULL, t.created LIMIT 20 OFFSET 0"
      }
      it("should build pagination with search and where") {
        val (w, o, l) = paginationFragment(prefix, Some(where), p.search("q"), sorts, fields)
        (w ++ o ++ l).query.sql shouldBe "WHERE t.id=? AND (t.name ILIKE ? OR t.desc ILIKE ?) ORDER BY t.created IS NULL, t.created LIMIT 20 OFFSET 0"
      }
      it("should build pagination with sort") {
        val (w, o, l) = paginationFragment(prefix, None, p.withOrderBy("name"), sorts, fields)
        (w ++ o ++ l).query.sql shouldBe " ORDER BY t.name IS NULL, t.name LIMIT 20 OFFSET 0"
      }
      it("should build pagination with sort search and where") {
        val (w, o, l) = paginationFragment(prefix, Some(where), p.withOrderBy("name"), sorts, fields)
        (w ++ o ++ l).query.sql shouldBe "WHERE t.id=? ORDER BY t.name IS NULL, t.name LIMIT 20 OFFSET 0"
      }
    }
  }
}

object QuerySpec {

  case class Entity(id: Int, name: String)

}
