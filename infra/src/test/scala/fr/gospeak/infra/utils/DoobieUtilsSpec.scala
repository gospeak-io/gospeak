package fr.gospeak.infra.utils

import doobie.implicits._
import doobie.util.fragment.Fragment
import fr.gospeak.infra.utils.DoobieUtils.Fragments._
import fr.gospeak.libs.scalautils.domain.Page
import org.scalatest.{FunSpec, Matchers}

class DoobieUtilsSpec extends FunSpec with Matchers {
  describe("DoobieUtils") {
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
