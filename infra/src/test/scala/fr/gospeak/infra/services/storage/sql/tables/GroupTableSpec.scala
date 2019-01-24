package fr.gospeak.infra.services.storage.sql.tables

import cats.data.NonEmptyList
import fr.gospeak.core.domain.Group
import fr.gospeak.core.domain.utils.Info
import fr.gospeak.infra.services.storage.sql.tables.GroupTable._
import fr.gospeak.infra.services.storage.sql.tables.testingutils.TableSpec

class GroupTableSpec extends TableSpec {
  private val slug = Group.Slug.from("ht-paris").get
  private val group = Group(groupId, slug, Group.Name("HT Paris"), "s", NonEmptyList.of(userId), Info(userId))

  describe("GroupTable") {
    describe("insert") {
      it("should generate the query") {
        val q = insert(group)
        q.sql shouldBe "INSERT INTO groups (id, slug, name, description, owners, created, created_by, updated, updated_by) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)"
        check(q)
      }
    }
    describe("selectOne") {
      it("should generate the query") {
        val q = selectOne(userId, slug)
        q.sql shouldBe "SELECT id, slug, name, description, owners, created, created_by, updated, updated_by FROM groups WHERE owners LIKE ? AND slug=?"
        check(q)
      }
    }
    describe("selectPage") {
      it("should generate the query") {
        val (s, c) = selectPage(userId, params)
        s.sql shouldBe "SELECT id, slug, name, description, owners, created, created_by, updated, updated_by FROM groups WHERE owners LIKE ? ORDER BY name OFFSET 0 LIMIT 20"
        c.sql shouldBe "SELECT count(*) FROM groups WHERE owners LIKE ? "
        check(s)
        check(c)
      }
    }
  }
}
