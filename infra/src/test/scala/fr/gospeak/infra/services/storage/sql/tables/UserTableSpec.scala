package fr.gospeak.infra.services.storage.sql.tables

import fr.gospeak.infra.services.storage.sql.tables.UserTable._
import fr.gospeak.infra.services.storage.sql.tables.testingutils.TableSpec
import fr.gospeak.libs.scalautils.domain.Email

class UserTableSpec extends TableSpec {
  describe("UserTable") {
    describe("insert") {
      it("should generate the query") {
        val q = insert(user)
        q.sql shouldBe "INSERT INTO users (id, first_name, last_name, email, created, updated) VALUES (?, ?, ?, ?, ?, ?)"
        check(q)
      }
    }
    describe("selectOne") {
      it("should generate query for Email") {
        val q = selectOne(user.email)
        q.sql shouldBe "SELECT id, first_name, last_name, email, created, updated FROM users WHERE email=?"
        check(q)
      }
      it("should generate the query for Id") {
        val q = selectOne(user.id)
        q.sql shouldBe "SELECT id, first_name, last_name, email, created, updated FROM users WHERE id=?"
        check(q)
      }
    }
    describe("selectAll") {
      it("should generate the query") {
        val q = selectAll(Seq(user.id))
        q.sql shouldBe "SELECT id, first_name, last_name, email, created, updated FROM users WHERE id IN (?)"
        check(q)
      }
    }
  }
}
