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
      it("should generate the query") {
        val q = selectOne(Email.from("john@mail.com").get)
        q.sql shouldBe "SELECT id, first_name, last_name, email, created, updated FROM users WHERE email=?"
        check(q)
      }
    }
  }
}
