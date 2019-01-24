package fr.gospeak.infra.services.storage.sql.tables

import fr.gospeak.core.domain._
import fr.gospeak.core.domain.utils.Info
import fr.gospeak.infra.services.storage.sql.tables.EventTable._
import fr.gospeak.infra.services.storage.sql.tables.testingutils.TableSpec

class EventTableSpec extends TableSpec {
  private val slug = Event.Slug("my-event")
  private val event = Event(groupId, eventId, slug, Event.Name("My Event"), Some("best talk"), None, Seq(Proposal.Id.generate()), Info(userId))

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
        val q = selectOne(groupId, slug)
        q.sql shouldBe "SELECT group_id, id, slug, name, description, venue, talks, created, created_by, updated, updated_by FROM events WHERE group_id=? AND slug=?"
        check(q)
      }
    }
    describe("selectPage") {
      it("should generate the query") {
        val (s, c) = selectPage(groupId, params)
        s.sql shouldBe "SELECT group_id, id, slug, name, description, venue, talks, created, created_by, updated, updated_by FROM events WHERE group_id=? ORDER BY name OFFSET 0 LIMIT 20"
        c.sql shouldBe "SELECT count(*) FROM events WHERE group_id=? "
        check(s)
        check(c)
      }
    }
  }
}
