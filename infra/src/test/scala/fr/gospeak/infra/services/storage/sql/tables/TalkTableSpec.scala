package fr.gospeak.infra.services.storage.sql.tables

import cats.data.NonEmptyList
import cats.effect.IO
import doobie.scalatest._
import fr.gospeak.core.domain.utils.{Info, Page}
import fr.gospeak.core.domain.{Talk, User}
import fr.gospeak.infra.services.storage.sql.tables.TalkTable._
import fr.gospeak.infra.testingutils.Values
import org.scalatest.{BeforeAndAfterAll, FunSpec, Matchers}

class TalkTableSpec extends FunSpec with Matchers with IOChecker with BeforeAndAfterAll {
  private val db = Values.db
  val transactor: doobie.Transactor[IO] = db.xa
  private val userId = User.Id.generate()
  private val talkId = Talk.Id.generate()
  private val slug = Talk.Slug("my-talk")
  private val talk = Talk(talkId, slug, Talk.Title("My Talk"), "best talk", NonEmptyList.of(userId), Info(userId))
  private val params = Page.Params()

  override def beforeAll(): Unit = db.createTables().unsafeRunSync()

  override def afterAll(): Unit = db.dropTables().unsafeRunSync()

  describe("TalkTable") {
    describe("insert") {
      it("should generate the query") {
        val q = insert(talk)
        q.sql shouldBe "INSERT INTO talks (id, slug, title, description, speakers, created, created_by, updated, updated_by) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)"
        check(q)
      }
    }
    describe("slugToId") {
      it("should generate the query") {
        val q = slugToId(userId, slug)
        q.sql shouldBe "SELECT id FROM talks WHERE slug=? AND speakers LIKE ?"
        check(q)
      }
    }
    describe("selectOne") {
      it("should generate the query") {
        val q = selectOne(talkId, userId)
        q.sql shouldBe "SELECT id, slug, title, description, speakers, created, created_by, updated, updated_by FROM talks WHERE id=? AND speakers LIKE ?"
        check(q)
      }
    }
    describe("selectPage") {
      it("should generate the query") {
        val (s, c) = selectPage(userId, params)
        s.sql shouldBe "SELECT id, slug, title, description, speakers, created, created_by, updated, updated_by FROM talks WHERE speakers LIKE ? ORDER BY title OFFSET 0 LIMIT 20"
        c.sql shouldBe "SELECT count(*) FROM talks WHERE speakers LIKE ? "
        check(s)
        check(c)
      }
    }
  }
}
