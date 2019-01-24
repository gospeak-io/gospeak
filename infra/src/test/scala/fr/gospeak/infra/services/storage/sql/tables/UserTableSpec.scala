package fr.gospeak.infra.services.storage.sql.tables

import java.time.Instant

import fr.gospeak.core.domain.User
import fr.gospeak.core.domain.utils.Email
import fr.gospeak.infra.services.storage.sql.tables.UserTable._
import fr.gospeak.infra.services.storage.sql.tables.testingutils.TableSpec

class UserTableSpec extends TableSpec {
  private val user = User(User.Id.generate(), "John", "Doe", Email("john@mail.com"), Instant.now(), Instant.now())

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
        val q = selectOne(Email("john@mail.com"))
        q.sql shouldBe "SELECT id, first_name, last_name, email, created, updated FROM users WHERE email=?"
        check(q)
      }
    }
  }
}
