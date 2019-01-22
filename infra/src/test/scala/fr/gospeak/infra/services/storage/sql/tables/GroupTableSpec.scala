package fr.gospeak.infra.services.storage.sql.tables

import cats.data.NonEmptyList
import cats.effect.IO
import doobie.scalatest.IOChecker
import fr.gospeak.core.domain.utils.{Info, Page}
import fr.gospeak.core.domain.{Group, User}
import fr.gospeak.infra.services.storage.sql.tables.GroupTable._
import fr.gospeak.infra.testingutils.Values
import org.scalatest.{BeforeAndAfterAll, FunSpec, Matchers}

class GroupTableSpec extends FunSpec with Matchers with IOChecker with BeforeAndAfterAll {
  private val db = Values.db
  val transactor: doobie.Transactor[IO] = db.xa
  private val userId = User.Id.generate()
  private val groupId = Group.Id.generate()
  private val slug = Group.Slug("ht-paris")
  private val group = Group(groupId, slug, Group.Name("HT Paris"), "s", NonEmptyList.of(userId), Info(userId))
  private val params = Page.Params()

  override def beforeAll(): Unit = db.createTables().unsafeRunSync()

  override def afterAll(): Unit = db.dropTables().unsafeRunSync()

  describe("GroupTable") {
    describe("insert") {
      it("should generate the query") {
        val q = insert(group)
        q.sql shouldBe "INSERT INTO groups (id, slug, name, description, owners, created, created_by, updated, updated_by) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)"
        check(q)
      }
    }
    describe("slugToId") {
      it("should generate the query") {
        val q = slugToId(slug)
        q.sql shouldBe "SELECT id FROM groups WHERE slug=?"
        check(q)
      }
    }
    describe("selectOwned") {
      it("should generate single query") {
        val q = selectOwned(groupId, userId)
        q.sql shouldBe "SELECT id, slug, name, description, owners, created, created_by, updated, updated_by FROM groups WHERE owners LIKE ? AND id=?"
        check(q)
      }
      it("should generate page query") {
        val (s, c) = selectOwned(userId, params)
        s.sql shouldBe "SELECT id, slug, name, description, owners, created, created_by, updated, updated_by FROM groups WHERE owners LIKE ? ORDER BY name OFFSET 0 LIMIT 20"
        c.sql shouldBe "SELECT count(*) FROM groups WHERE owners LIKE ? "
        check(s)
        check(c)
      }
    }
    describe("selectWithCfp") {
      it("should generate single query") {
        val q = selectWithCfp(groupId)
        q.sql shouldBe "SELECT id, slug, name, description, owners, created, created_by, updated, updated_by FROM groups WHERE id=?"
        check(q)
      }
      it("should generate page query") {
        val (s, c) = selectWithCfp(params)
        s.sql shouldBe "SELECT id, slug, name, description, owners, created, created_by, updated, updated_by FROM groups ORDER BY name OFFSET 0 LIMIT 20"
        c.sql shouldBe "SELECT count(*) FROM groups "
        check(s)
        check(c)
      }
    }
  }

}
