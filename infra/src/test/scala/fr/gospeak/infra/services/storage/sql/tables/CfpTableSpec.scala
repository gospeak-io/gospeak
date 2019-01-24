package fr.gospeak.infra.services.storage.sql.tables

import fr.gospeak.core.domain.Cfp
import fr.gospeak.core.domain.utils.Info
import fr.gospeak.infra.services.storage.sql.tables.CfpTable._
import fr.gospeak.infra.services.storage.sql.tables.testingutils.TableSpec

class CfpTableSpec extends TableSpec {
  private val slug = Cfp.Slug("slug")
  private val cfp = Cfp(cfpId, slug, Cfp.Name("Name"), "desc", groupId, Info(userId))

  describe("CfpTable") {
    describe("insert") {
      it("should generate the query") {
        val q = insert(cfp)
        q.sql shouldBe "INSERT INTO cfps (id, slug, name, description, group_id, created, created_by, updated, updated_by) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)"
        check(q)
      }
    }
    describe("selectOne") {
      it("should generate query for cfp slug") {
        val q = selectOne(slug)
        q.sql shouldBe "SELECT id, slug, name, description, group_id, created, created_by, updated, updated_by FROM cfps WHERE slug=?"
        check(q)
      }
      it("should generate query for cfp id") {
        val q = selectOne(cfpId)
        q.sql shouldBe "SELECT id, slug, name, description, group_id, created, created_by, updated, updated_by FROM cfps WHERE id=?"
        check(q)
      }
      it("should generate query for group id") {
        val q = selectOne(groupId)
        q.sql shouldBe "SELECT id, slug, name, description, group_id, created, created_by, updated, updated_by FROM cfps WHERE group_id=?"
        check(q)
      }
    }
    describe("selectPage") {
      it("should generate the query") {
        val (s, c) = selectPage(params)
        s.sql shouldBe "SELECT id, slug, name, description, group_id, created, created_by, updated, updated_by FROM cfps ORDER BY name OFFSET 0 LIMIT 20"
        c.sql shouldBe "SELECT count(*) FROM cfps "
        check(s)
        check(c)
      }
    }
  }
}
