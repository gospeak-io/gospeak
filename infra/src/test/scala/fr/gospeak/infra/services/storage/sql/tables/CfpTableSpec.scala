package fr.gospeak.infra.services.storage.sql.tables

import cats.effect.IO
import doobie.scalatest.IOChecker
import fr.gospeak.core.domain.utils.{Info, Page}
import fr.gospeak.core.domain.{Cfp, Group, User}
import fr.gospeak.infra.services.storage.sql.tables.CfpTable._
import fr.gospeak.infra.testingutils.Values
import org.scalatest.{BeforeAndAfterAll, FunSpec, Matchers}

class CfpTableSpec extends FunSpec with Matchers with IOChecker with BeforeAndAfterAll {
  private val db = Values.db
  val transactor: doobie.Transactor[IO] = db.xa
  private val userId = User.Id.generate()
  private val groupId = Group.Id.generate()
  private val cfpId = Cfp.Id.generate()
  private val slug = Cfp.Slug("slug")
  private val cfp = Cfp(cfpId, slug, Cfp.Name("Name"), "desc", groupId, Info(userId))
  private val params = Page.Params()

  override def beforeAll(): Unit = db.createTables().unsafeRunSync()

  override def afterAll(): Unit = db.dropTables().unsafeRunSync()

  describe("CfpTable") {
    describe("insert") {
      it("should generate the query") {
        val q = insert(cfp)
        q.sql shouldBe "INSERT INTO cfps (id, slug, name, description, group_id, created, created_by, updated, updated_by) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)"
        check(q)
      }
    }
    describe("slugToId") {
      it("should generate the query") {
        val q = slugToId(slug)
        q.sql shouldBe "SELECT id FROM cfps WHERE slug=?"
        check(q)
      }
    }
    describe("selectOne") {
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
