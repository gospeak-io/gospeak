package fr.gospeak.infra.utils

import doobie.implicits._
import doobie.util.fragment.Fragment
import fr.gospeak.core.domain.utils.Page
import org.scalatest.{FunSpec, Matchers}

class DoobieUtilsSpec extends FunSpec with Matchers {

  import DoobieUtils._

  describe("DoobieUtils") {
    describe("Fragments") {
      import Fragments._
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
      describe("pagination") {
        val p = Page.Params(Some(Page.Search("q")), Some(Page.OrderBy("name")), Page.No(3), Page.Size(20))
        it("should build pagination sql") {
          val fr = paginate(p, searchFields, defaultSort)
          fr.toString shouldBe """Fragment("WHERE name LIKE '%?%' OR description LIKE '%?%' ORDER BY name OFFSET 40 LIMIT 20")"""
        }
        it("should not include search when not present") {
          val fr = paginate(p.copy(search = None), searchFields, defaultSort)
          fr.toString shouldBe """Fragment("ORDER BY name OFFSET 40 LIMIT 20")"""
        }
        it("should include default order when not present") {
          val fr = paginate(p.copy(orderBy = None), searchFields, defaultSort)
          fr.toString shouldBe """Fragment("WHERE name LIKE '%?%' OR description LIKE '%?%' ORDER BY id OFFSET 40 LIMIT 20")"""
        }
        it("should not include search when not searchFields") {
          val fr = paginate(p, Seq(), defaultSort)
          fr.toString shouldBe """Fragment("ORDER BY name OFFSET 40 LIMIT 20")"""
        }
        it("should include where clause") {
          val fr = paginate(p, searchFields, defaultSort, whereOpt)
          fr.toString shouldBe """Fragment("WHERE id=? AND (name LIKE '%?%' OR description LIKE '%?%' ) ORDER BY name OFFSET 40 LIMIT 20")"""
        }
        it("should include where clause when present but no search") {
          val fr = paginate(p.copy(search = None), searchFields, defaultSort, whereOpt)
          fr.toString shouldBe """Fragment("WHERE id=? ORDER BY name OFFSET 40 LIMIT 20")"""
        }
      }
    }
  }
}
