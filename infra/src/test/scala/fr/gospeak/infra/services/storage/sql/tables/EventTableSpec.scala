package fr.gospeak.infra.services.storage.sql.tables

import cats.effect.IO
import doobie.scalatest.IOChecker
import fr.gospeak.core.domain._
import fr.gospeak.core.domain.utils.{Info, Page}
import fr.gospeak.infra.services.storage.sql.tables.EventTable._
import fr.gospeak.infra.testingutils.Values
import org.scalatest.{BeforeAndAfterAll, FunSpec, Matchers}

class EventTableSpec extends FunSpec with Matchers with IOChecker with BeforeAndAfterAll {
  private val db = Values.db
  val transactor: doobie.Transactor[IO] = db.xa
  private val userId = User.Id.generate()
  private val groupId = Group.Id.generate()
  private val eventId = Event.Id.generate()
  private val slug = Event.Slug("my-event")
  private val event = Event(groupId, eventId, slug, Event.Name("My Event"), Some("best talk"), None, Seq(Proposal.Id.generate()), Info(userId))
  private val params = Page.Params()

  override def beforeAll(): Unit = db.createTables().unsafeRunSync()

  override def afterAll(): Unit = db.dropTables().unsafeRunSync()

  describe("EventTable") {
    describe("insert") {
      it("should generate the query") {
        val q = insert(event)
        q.sql shouldBe "INSERT INTO events (group_id, id, slug, name, description, venue, talks, created, created_by, updated, updated_by) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)"
        check(q)
      }
    }
    describe("slugToId") {
      it("should generate the query") {
        val q = slugToId(groupId, slug)
        q.sql shouldBe "SELECT id FROM events WHERE slug=? AND group_id=?"
        check(q)
      }
    }
    describe("selectOne") {
      it("should generate the query") {
        val q = selectOne(eventId)
        q.sql shouldBe "SELECT group_id, id, slug, name, description, venue, talks, created, created_by, updated, updated_by FROM events WHERE id=?"
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
