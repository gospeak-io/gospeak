package fr.gospeak.infra.services.storage.sql.tables

import fr.gospeak.infra.services.storage.sql.tables.EventTable._
import fr.gospeak.infra.services.storage.sql.tables.testingutils.TableSpec

class EventTableSpec extends TableSpec {
  describe("EventTable") {
    describe("insert") {
      it("should generate the query") {
        val q = insert(event)
        q.sql shouldBe "INSERT INTO events (group_id, id, slug, name, description, venue, talks, created, created_by, updated, updated_by) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)"
        check(q)
      }
    }
    describe("selectOne") {
      it("should generate the query") {
        val q = selectOne(group.id, event.slug)
        q.sql shouldBe "SELECT group_id, id, slug, name, description, venue, talks, created, created_by, updated, updated_by FROM events WHERE group_id=? AND slug=?"
        check(q)
      }
    }
    describe("selectPage") {
      it("should generate the query") {
        val (s, c) = selectPage(group.id, params)
        s.sql shouldBe "SELECT group_id, id, slug, name, description, venue, talks, created, created_by, updated, updated_by FROM events WHERE group_id=? ORDER BY name OFFSET 0 LIMIT 20"
        c.sql shouldBe "SELECT count(*) FROM events WHERE group_id=? "
        check(s)
        check(c)
      }
    }
  }
}
