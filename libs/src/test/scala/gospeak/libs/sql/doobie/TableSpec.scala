package gospeak.libs.sql.doobie

import gospeak.libs.scala.Extensions._
import gospeak.libs.sql.doobie.Table.Sort
import gospeak.libs.testingutils.BaseSpec

class TableSpec extends BaseSpec {
  describe("Table") {
    val table1 = Table.from("table1", "t1", List("id", "name"), Sort("name", "t1"), List("name"), List()).get
    val table2 = Table.from("table2", "t2", List("id", "ref1"), Sort("id", "t2"), List("ref1"), List()).get
    val table3 = Table.from("table3", "t3", List("id", "ref2"), Sort("id", "t3"), List("ref2"), List()).get
    describe("from") {
      it("should not have search field present in fields") {
        Table.from("tab", "t", List("f"), Sort("f", "t"), List(), List()) shouldBe a[Right[_, _]]
        Table.from("tab", "t", List("f", "f"), Sort("f", "t"), List(), List()) shouldBe a[Left[_, _]] // duplicated field
        // Table.from("tab", "t", List("f"), List("s"), Sort("f", "t"), List()) shouldBe a[Left[_, _]] // unknown sort
        Table.from("tab", "t", List("f"), Sort("f", "t"), List("s"), List()) shouldBe a[Left[_, _]] // unknown search
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
        val fields = List(Field("id", "t1"), Field("name", "t1"))
        table1.fields shouldBe fields

        table1.dropField(Field("name", "t1")).get.fields shouldBe List(Field("id", "t1"))
        table1.dropField(Field("miss", "t1")) shouldBe a[Left[_, _]]
        table1.dropField(Field("name", "m1")) shouldBe a[Left[_, _]]
        table1.dropField(Field("miss", "m1")) shouldBe a[Left[_, _]]

        table1.dropField(_.field("name", "t1")).get.fields shouldBe List(Field("id", "t1"))
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
}
