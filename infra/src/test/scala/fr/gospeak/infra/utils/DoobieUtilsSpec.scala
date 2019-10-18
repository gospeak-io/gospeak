package fr.gospeak.infra.utils

import doobie.implicits._
import doobie.util.fragment.Fragment
import fr.gospeak.infra.utils.DoobieUtils.Fragments._
import fr.gospeak.infra.utils.DoobieUtils.{Field, Table}
import fr.gospeak.libs.scalautils.Extensions._
import fr.gospeak.libs.scalautils.domain.Page
import org.scalatest.{FunSpec, Matchers}

class DoobieUtilsSpec extends FunSpec with Matchers {
  describe("DoobieUtils") {
    describe("Table") {
      val table1 = Table.from("table1", "t1", Seq("id", "name"), Seq("name"), Seq("name")).get
      val table2 = Table.from("table2", "t2", Seq("id", "ref1"), Seq("id"), Seq("ref1")).get
      val table3 = Table.from("table3", "t3", Seq("id", "ref2"), Seq("id"), Seq("ref2")).get
      describe("from") {
        it("should not have sort and search field present in fields") {
          Table.from("tab", "t", Seq("f"), Seq(), Seq()) shouldBe a[Right[_, _]]
          Table.from("tab", "t", Seq("f", "f"), Seq(), Seq()) shouldBe a[Left[_, _]] // duplicated field
          Table.from("tab", "t", Seq("f"), Seq("s"), Seq()) shouldBe a[Left[_, _]] // unknown sort
          Table.from("tab", "t", Seq("f"), Seq(), Seq("s")) shouldBe a[Left[_, _]] // unknown search
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
          val table = table1.join(table2, _.field("id"), _.field("ref1")).get
          table.name shouldBe "table1 t1 INNER JOIN table2 t2 ON t1.id=t2.ref1"
          table.prefix shouldBe "t1"
          table.fields shouldBe table1.fields ++ table2.fields
          table.sort shouldBe table1.sort
          table.search shouldBe table1.search ++ table2.search
        }
        it("should join multiple tables") {
          val table = table1
            .join(table2, _.field("id"), _.field("ref1")).get
            .join(table3, _.field("id", "t2"), _.field("ref2")).get
          table.name shouldBe "table1 t1 INNER JOIN table2 t2 ON t1.id=t2.ref1 INNER JOIN table3 t3 ON t2.id=t3.ref2"
        }
        // FIXME: join should be associative so I should keep a join list instead of merging strings :(
        ignore("should be associative") {
          val t23 = table2.join(table3, _.field("id"), _.field("ref2")).get
          val res1 = table1.join(t23, _.field("id"), _.field("ref1")).get

          val t12 = table1.join(table2, _.field("id"), _.field("ref1")).get
          val res2 = t12.join(table3, _.field("id", "t2"), _.field("ref2")).get

          t23.name shouldBe "table2 t2 INNER JOIN table3 t3 ON t2.id=t3.ref2"
          res1.name shouldBe "table1 t1 INNER JOIN table2 t2 INNER JOIN table3 t3 ON t2.id=t3.ref2 ON t1.id=t2.ref1"

          t12.name shouldBe "table1 t1 INNER JOIN table2 t2 ON t1.id=t2.ref1"
          res2.name shouldBe "table1 t1 INNER JOIN table2 t2 ON t1.id=t2.ref1 INNER JOIN table3 t3 ON t2.id=t3.ref2"

          res1.name shouldBe res2.name
        }
      }
    }
    describe("Fragments") {
      val table = Fragment.const0("mytable")
      val fields = Fragment.const0("id, name, description")
      val searchFields = Seq("name", "description")
      val defaultSort = Page.OrderBy("id")
      val id = 123
      val name = "Jean"
      val description = "speaker"
      val values = fr0"$id, $name, $description"
      val whereOpt = Some(fr0"WHERE id=$id")
      describe("buildInsert") {
        it("should build insert into statement") {
          buildInsert(table, fields, values).toString() shouldBe """Fragment("INSERT INTO mytable (id, name, description) VALUES (?, ?, ?)")"""
        }
      }
      describe("buildSelect") {
        it("should build select statement") {
          buildSelect(table, fields).toString() shouldBe """Fragment("SELECT id, name, description FROM mytable")"""
        }
        it("should build select statement with where clause") {
          buildSelect(table, fields, whereOpt.get).toString() shouldBe """Fragment("SELECT id, name, description FROM mytable WHERE id=?")"""
        }
      }
      describe("buildUpdate") {
        it("should build update statement") {
          buildUpdate(table, fr0"name=$name", whereOpt.get).toString() shouldBe """Fragment("UPDATE mytable SET name=? WHERE id=?")"""
        }
      }
      describe("pagination") {
        val p = Page.Params(Page.No(3), Page.Size(20), Some(Page.Search("q")), Some(Page.OrderBy("name")))
        it("should build pagination sql") {
          Paginate(p, searchFields, defaultSort).all.toString() shouldBe Paginate(
            where = fr0"WHERE name ILIKE ? OR description ILIKE ? ",
            orderBy = fr0"ORDER BY name IS NULL, name ",
            limit = fr0"OFFSET 40 LIMIT 20").all.toString()
        }
        it("should not include search when not present") {
          Paginate(p.copy(search = None), searchFields, defaultSort).all.toString() shouldBe Paginate(
            where = fr0"",
            orderBy = fr0"ORDER BY name IS NULL, name ",
            limit = fr0"OFFSET 40 LIMIT 20").all.toString()
        }
        it("should include default order when not present") {
          Paginate(p.copy(orderBy = None), searchFields, defaultSort).all.toString() shouldBe Paginate(
            where = fr0"WHERE name ILIKE ? OR description ILIKE ? ",
            orderBy = fr0"ORDER BY id IS NULL, id ",
            limit = fr0"OFFSET 40 LIMIT 20").all.toString()
        }
        it("should not include search when not searchFields") {
          Paginate(p, Seq(), defaultSort).all.toString() shouldBe Paginate(
            where = fr0"",
            orderBy = fr0"ORDER BY name IS NULL, name ",
            limit = fr0"OFFSET 40 LIMIT 20").all.toString()
        }
        it("should include where clause") {
          Paginate(p, searchFields, defaultSort, whereOpt).all.toString() shouldBe Paginate(
            where = fr0"WHERE id=? AND (name ILIKE ? OR description ILIKE ? ) ",
            orderBy = fr0"ORDER BY name IS NULL, name ",
            limit = fr0"OFFSET 40 LIMIT 20").all.toString()
        }
        it("should include where clause when present but no search") {
          Paginate(p.copy(search = None), searchFields, defaultSort, whereOpt).all.toString() shouldBe Paginate(
            where = fr0"WHERE id=? ",
            orderBy = fr0"ORDER BY name IS NULL, name ",
            limit = fr0"OFFSET 40 LIMIT 20").all.toString()
        }
      }
    }
  }
}
