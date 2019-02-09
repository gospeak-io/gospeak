package fr.gospeak.infra.services.storage.sql.tables

import fr.gospeak.infra.services.storage.sql.tables.CfpTable._
import fr.gospeak.infra.services.storage.sql.tables.testingutils.TableSpec

class CfpTableSpec extends TableSpec {
  describe("CfpTable") {
    describe("insert") {
      it("should generate the query") {
        val q = insert(cfp)
        q.sql shouldBe "INSERT INTO cfps (id, group_id, slug, name, description, created, created_by, updated, updated_by) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)"
        check(q)
      }
    }
    describe("selectOne") {
      it("should generate query for cfp slug") {
        val q = selectOne(cfp.slug)
        q.sql shouldBe "SELECT id, group_id, slug, name, description, created, created_by, updated, updated_by FROM cfps WHERE slug=?"
        check(q)
      }
      it("should generate query for cfp id") {
        val q = selectOne(cfp.id)
        q.sql shouldBe "SELECT id, group_id, slug, name, description, created, created_by, updated, updated_by FROM cfps WHERE id=?"
        check(q)
      }
      it("should generate query for group id") {
        val q = selectOne(group.id)
        q.sql shouldBe "SELECT id, group_id, slug, name, description, created, created_by, updated, updated_by FROM cfps WHERE group_id=?"
        check(q)
      }
    }
    describe("selectPage") {
      it("should generate the query") {
        val (s, c) = selectPage(talk.id, params)
        s.sql shouldBe "SELECT id, group_id, slug, name, description, created, created_by, updated, updated_by FROM cfps WHERE id NOT IN (SELECT cfp_id FROM proposals WHERE talk_id = ?) ORDER BY name OFFSET 0 LIMIT 20"
        c.sql shouldBe "SELECT count(*) FROM cfps WHERE id NOT IN (SELECT cfp_id FROM proposals WHERE talk_id = ?) "
        check(s)
        check(c)
      }
    }
  }
}
