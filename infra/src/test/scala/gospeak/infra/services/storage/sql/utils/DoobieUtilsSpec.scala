package gospeak.infra.services.storage.sql.utils

import cats.data.NonEmptyList
import doobie.implicits._
import gospeak.infra.services.storage.sql.utils.DoobieUtils._
import gospeak.infra.services.storage.sql.utils.DoobieUtilsSpec.Entity
import gospeak.libs.scala.Extensions._
import gospeak.libs.scala.domain.Page
import org.scalatest.{FunSpec, Matchers}

class DoobieUtilsSpec extends FunSpec with Matchers {
  describe("DoobieUtils") {
    describe("Table") {
      val table1 = Table.from("table1", "t1", Seq("id", "name"), Seq("name"), Seq("name"), Seq()).get
      val table2 = Table.from("table2", "t2", Seq("id", "ref1"), Seq("id"), Seq("ref1"), Seq()).get
      val table3 = Table.from("table3", "t3", Seq("id", "ref2"), Seq("id"), Seq("ref2"), Seq()).get
      describe("from") {
        it("should not have sort and search field present in fields") {
          Table.from("tab", "t", Seq("f"), Seq(), Seq(), Seq()) shouldBe a[Right[_, _]]
          Table.from("tab", "t", Seq("f", "f"), Seq(), Seq(), Seq()) shouldBe a[Left[_, _]] // duplicated field
          // Table.from("tab", "t", Seq("f"), Seq("s"), Seq(), Seq()) shouldBe a[Left[_, _]] // unknown sort
          Table.from("tab", "t", Seq("f"), Seq(), Seq("s"), Seq()) shouldBe a[Left[_, _]] // unknown search
        }
      }
      describe("field") {
        it("should get a field by name or return an error") {
          table1.field("name") shouldBe Right(Field("name", "t1"))
          table1.field("miss") shouldBe a[Left[_, _]]
        }
        it("should get a field by name and prefix or return an error") {
          table1.field("name", "t1") shouldBe Right(Field("name", "t1"))
          table1.field("miss", "t1") shouldBe a[Left[_, _]]
          table1.field("name", "m1") shouldBe a[Left[_, _]]
          table1.field("miss", "m1") shouldBe a[Left[_, _]]
        }
      }
      describe("dropField") {
        it("should remove a field") {
          val fields = Seq(Field("id", "t1"), Field("name", "t1"))
          table1.fields shouldBe fields

          table1.dropField(Field("name", "t1")).get.fields shouldBe Seq(Field("id", "t1"))
          table1.dropField(Field("miss", "t1")) shouldBe a[Left[_, _]]
          table1.dropField(Field("name", "m1")) shouldBe a[Left[_, _]]
          table1.dropField(Field("miss", "m1")) shouldBe a[Left[_, _]]

          table1.dropField(_.field("name", "t1")).get.fields shouldBe Seq(Field("id", "t1"))
          table1.dropField(_.field("miss", "t1")) shouldBe a[Left[_, _]]
        }
      }
      describe("join") {
        it("should build a joined table") {
          val table = table1.join(table2, _.field("id") -> _.field("ref1")).get
          table.value.query.sql shouldBe "table1 t1 INNER JOIN table2 t2 ON t1.id=t2.ref1"
          table.prefix shouldBe "t1"
          table.fields shouldBe table1.fields ++ table2.fields
          table.sorts shouldBe table1.sorts
          table.search shouldBe table1.search ++ table2.search
        }
        it("should join multiple tables") {
          val table = table1
            .join(table2, _.field("id") -> _.field("ref1")).get
            .join(table3, _.field("id", "t2") -> _.field("ref2")).get
          table.value.query.sql shouldBe "table1 t1 INNER JOIN table2 t2 ON t1.id=t2.ref1 INNER JOIN table3 t3 ON t2.id=t3.ref2"
        }
        it("should be associative") {
          val t23 = table2.join(table3, _.field("id") -> _.field("ref2")).get
          t23.value.query.sql shouldBe "table2 t2 INNER JOIN table3 t3 ON t2.id=t3.ref2"

          val t123A = table1.join(t23, _.field("id") -> _.field("ref1")).get
          t123A.value.query.sql shouldBe "table1 t1 INNER JOIN table2 t2 ON t1.id=t2.ref1 INNER JOIN table3 t3 ON t2.id=t3.ref2"

          val t12 = table1.join(table2, _.field("id") -> _.field("ref1")).get
          t12.value.query.sql shouldBe "table1 t1 INNER JOIN table2 t2 ON t1.id=t2.ref1"

          val t123B = t12.join(table3, _.field("id", "t2") -> _.field("ref2")).get
          t123B.value.query.sql shouldBe "table1 t1 INNER JOIN table2 t2 ON t1.id=t2.ref1 INNER JOIN table3 t3 ON t2.id=t3.ref2"

          t123A shouldBe t123B
        }
      }
      describe("Dynamic") {
        it("should select a field") {
          table1.id shouldBe Right(Field("id", "t1"))
          table1.miss shouldBe a[Left[_, _]]
        }
      }
    }
    describe("Insert") {
      it("should build an insert") {
        val entity = Entity(1, "n")
        val sql = Insert[Entity]("table", Seq(Field("id", "t"), Field("name", "t")), entity, e => fr0"${e.id}, ${e.name}").fr.update.sql
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
        val sql = Select(fr0"table t", Seq(Field("id", "t"), Field("name", "t")), Seq(), Seq(), Some(fr0" WHERE t.id=${e.id}"), Sorts(Seq(Field("name", "t")), Map()), Some(3)).fr.update.sql
        sql shouldBe "SELECT t.id, t.name FROM table t WHERE t.id=? ORDER BY t.name IS NULL, t.name LIMIT 3"
      }
    }
    describe("SelectPage") {
      it("should build a select") {
        val e = Entity(1, "n")
        // val sql = SelectPage("table t", Seq(Field("id", "t"), Field("name", "t")), Some(fr0"WHERE t.id=${e.id}")).fr.update.sql
        // sql shouldBe "SELECT t.id, t.name FROM table t WHERE t.id=?"
      }
    }
    describe("whereFragment") {
      it("should build the where fragment") {
        val e = Entity(1, "n")
        val fields = Seq(Field("name", "t"), Field("desc", "t"))
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
      val fields = Seq(Field("name", "t"), Field("desc", "t"))
      val sorts = Sorts(Seq(Field("created", "t")), Map())
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
        val (w, o, l) = paginationFragment(prefix, None, p.orderBy("name"), sorts, fields)
        (w ++ o ++ l).query.sql shouldBe " ORDER BY t.name IS NULL, t.name LIMIT 20 OFFSET 0"
      }
      it("should build pagination with sort search and where") {
        val (w, o, l) = paginationFragment(prefix, Some(where), p.orderBy("name"), sorts, fields)
        (w ++ o ++ l).query.sql shouldBe "WHERE t.id=? ORDER BY t.name IS NULL, t.name LIMIT 20 OFFSET 0"
      }
    }
  }
}

object DoobieUtilsSpec {

  case class Entity(id: Int, name: String)

}
