package fr.gospeak.infra.services.storage.sql.tables

import cats.data.NonEmptyList
import fr.gospeak.core.domain.Proposal
import fr.gospeak.infra.services.storage.sql.tables.ProposalTable._
import fr.gospeak.infra.services.storage.sql.tables.testingutils.TableSpec

class ProposalTableSpec extends TableSpec {
  describe("ProposalTable") {
    describe("insert") {
      it("should generate the query") {
        val q = insert(proposal)
        q.sql shouldBe "INSERT INTO proposals (id, talk_id, cfp_id, event_id, title, duration, status, description, speakers, slides, video, created, created_by, updated, updated_by) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)"
        check(q)
      }
    }
    describe("updateStatus") {
      it("should generate the query") {
        val q = updateStatus(proposal.id)(proposal.status, None, user.id, now)
        q.sql shouldBe "UPDATE proposals SET status=?, event_id=?, updated=?, updated_by=? WHERE id=?"
        check(q)
      }
    }
    describe("selectOne") {
      it("should generate query for proposal id") {
        val q = selectOne(proposal.id)
        q.sql shouldBe "SELECT id, talk_id, cfp_id, event_id, title, duration, status, description, speakers, slides, video, created, created_by, updated, updated_by FROM proposals WHERE id=?"
        check(q)
      }
      it("should generate query for talk and cfp ids") {
        val q = selectOne(talk.id, cfp.id)
        q.sql shouldBe "SELECT id, talk_id, cfp_id, event_id, title, duration, status, description, speakers, slides, video, created, created_by, updated, updated_by FROM proposals WHERE talk_id=? AND cfp_id=?"
        check(q)
      }
    }
    describe("selectPage") {
      it("should generate query for a talk") {
        val (s, c) = selectPage(talk.id, params)
        s.sql shouldBe
          "SELECT c.id, c.group_id, c.slug, c.name, c.description, c.created, c.created_by, c.updated, c.updated_by, " +
            "p.id, p.talk_id, p.cfp_id, p.event_id, p.title, p.duration, p.status, p.description, p.speakers, p.slides, p.video, p.created, p.created_by, p.updated, p.updated_by " +
            "FROM cfps c INNER JOIN proposals p ON p.cfp_id=c.id WHERE p.talk_id=? ORDER BY p.created DESC OFFSET 0 LIMIT 20"
        c.sql shouldBe "SELECT count(*) FROM cfps c INNER JOIN proposals p ON p.cfp_id=c.id WHERE p.talk_id=? "
        check(s)
        check(c)
      }
      it("should generate query for a cfp") {
        val (s, c) = selectPage(cfp.id, params)
        s.sql shouldBe "SELECT id, talk_id, cfp_id, event_id, title, duration, status, description, speakers, slides, video, created, created_by, updated, updated_by FROM proposals WHERE cfp_id=? ORDER BY created DESC OFFSET 0 LIMIT 20"
        c.sql shouldBe "SELECT count(*) FROM proposals WHERE cfp_id=? "
        check(s)
        check(c)
      }
      it("should generate query for a cfp and status") {
        val (s, c) = selectPage(cfp.id, Proposal.Status.Pending, params)
        s.sql shouldBe "SELECT id, talk_id, cfp_id, event_id, title, duration, status, description, speakers, slides, video, created, created_by, updated, updated_by FROM proposals WHERE cfp_id=? AND status=? ORDER BY created DESC OFFSET 0 LIMIT 20"
        c.sql shouldBe "SELECT count(*) FROM proposals WHERE cfp_id=? AND status=? "
        check(s)
        check(c)
      }
    }
    describe("selectAll") {
      it("should generate the query") {
        val q = selectAll(NonEmptyList.of(proposal.id))
        q.sql shouldBe "SELECT id, talk_id, cfp_id, event_id, title, duration, status, description, speakers, slides, video, created, created_by, updated, updated_by FROM proposals WHERE id IN (?) "
        check(q)
      }
    }
  }
}
