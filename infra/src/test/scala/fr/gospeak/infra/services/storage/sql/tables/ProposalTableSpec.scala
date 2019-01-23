package fr.gospeak.infra.services.storage.sql.tables

import cats.effect.IO
import doobie.scalatest.IOChecker
import fr.gospeak.core.domain._
import fr.gospeak.core.domain.utils.{Info, Page}
import fr.gospeak.infra.services.storage.sql.tables.ProposalTable._
import fr.gospeak.infra.testingutils.Values
import org.scalatest.{BeforeAndAfterAll, FunSpec, Matchers}

class ProposalTableSpec extends FunSpec with Matchers with IOChecker with BeforeAndAfterAll {
  private val db = Values.db
  val transactor: doobie.Transactor[IO] = db.xa
  private val userId = User.Id.generate()
  private val talkId = Talk.Id.generate()
  private val cfpId = Cfp.Id.generate()
  private val proposalId = Proposal.Id.generate()
  private val proposal = Proposal(proposalId, talkId, cfpId, Talk.Title("My Proposal"), "best talk", Info(userId))
  private val params = Page.Params()

  override def beforeAll(): Unit = db.createTables().unsafeRunSync()

  override def afterAll(): Unit = db.dropTables().unsafeRunSync()

  describe("ProposalTable") {
    describe("insert") {
      it("should generate the query") {
        val q = insert(proposal)
        q.sql shouldBe "INSERT INTO proposals (id, talk_id, cfp_id, title, description, created, created_by, updated, updated_by) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)"
        check(q)
      }
    }
    describe("selectOne") {
      it("should generate the query") {
        val q = selectOne(proposalId)
        q.sql shouldBe "SELECT id, talk_id, cfp_id, title, description, created, created_by, updated, updated_by FROM proposals WHERE id=?"
        check(q)
      }
    }
    describe("selectPage for group") {
      it("should generate the query") {
        val (s, c) = selectPage(cfpId, params)
        s.sql shouldBe "SELECT id, talk_id, cfp_id, title, description, created, created_by, updated, updated_by FROM proposals WHERE cfp_id=? ORDER BY title OFFSET 0 LIMIT 20"
        c.sql shouldBe "SELECT count(*) FROM proposals WHERE cfp_id=? "
        check(s)
        check(c)
      }
    }
    describe("selectPage for talk") {
      it("should generate the query") {
        val (s, c) = selectPage(talkId, params)
        s.sql shouldBe "SELECT c.id, c.slug, c.name, c.description, c.group_id, c.created, c.created_by, c.updated, c.updated_by, p.id, p.talk_id, p.cfp_id, p.title, p.description, p.created, p.created_by, p.updated, p.updated_by FROM cfps c INNER JOIN proposals p ON p.cfp_id=c.id WHERE p.talk_id=? ORDER BY p.title OFFSET 0 LIMIT 20"
        c.sql shouldBe "SELECT count(*) FROM cfps c INNER JOIN proposals p ON p.cfp_id=c.id WHERE p.talk_id=? "
        check(s)
        check(c)
      }
    }
  }
}
