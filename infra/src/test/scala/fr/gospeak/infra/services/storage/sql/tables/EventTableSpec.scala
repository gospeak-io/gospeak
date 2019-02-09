package fr.gospeak.infra.services.storage.sql.tables

import fr.gospeak.infra.services.storage.sql.tables.EventTable._
import fr.gospeak.infra.services.storage.sql.tables.testingutils.TableSpec

class EventTableSpec extends TableSpec {
  describe("EventTable") {
    describe("insert") {
      it("should generate the query") {
        val q = insert(event)
        q.sql shouldBe "INSERT INTO events (id, group_id, slug, name, start, description, venue, talks, created, created_by, updated, updated_by) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)"
        check(q)
      }
    }
    describe("selectOne") {
      it("should generate the query") {
        val q = selectOne(group.id, event.slug)
        q.sql shouldBe "SELECT id, group_id, slug, name, start, description, venue, talks, created, created_by, updated, updated_by FROM events WHERE group_id=? AND slug=?"
        check(q)
      }
    }
    describe("selectPage") {
      it("should generate the query") {
        val (s, c) = selectPage(group.id, params)
        s.sql shouldBe "SELECT id, group_id, slug, name, start, description, venue, talks, created, created_by, updated, updated_by FROM events WHERE group_id=? ORDER BY start DESC OFFSET 0 LIMIT 20"
        c.sql shouldBe "SELECT count(*) FROM events WHERE group_id=? "
        check(s)
        check(c)
      }
    }
    describe("selectAllAfter") {
      it("should generate the query") {
        val (s, c) = selectAllAfter(group.id, now, params)
        s.sql shouldBe "SELECT id, group_id, slug, name, start, description, venue, talks, created, created_by, updated, updated_by FROM events WHERE group_id=? AND start > ? ORDER BY start DESC OFFSET 0 LIMIT 20"
        c.sql shouldBe "SELECT count(*) FROM events WHERE group_id=? AND start > ? "
        check(s)
        check(c)
      }
    }
    describe("update") {
      it("should generate the query") {
        val q = update(group.id, event.slug)(event.data, user.id, now)
        q.sql shouldBe "UPDATE events SET slug=?, name=?, start=?, updated=?, updated_by=? WHERE group_id=? AND slug=?"
        check(q)
      }
    }
  }
}
