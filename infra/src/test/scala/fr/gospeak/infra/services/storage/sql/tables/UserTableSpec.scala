package fr.gospeak.infra.services.storage.sql.tables

import java.time.Instant

import cats.effect.IO
import doobie.scalatest.IOChecker
import fr.gospeak.core.domain.User
import fr.gospeak.core.domain.utils.Email
import fr.gospeak.infra.services.storage.sql.tables.UserTable._
import fr.gospeak.infra.testingutils.Values
import org.scalatest.{BeforeAndAfterAll, FunSpec, Matchers}

class UserTableSpec extends FunSpec with Matchers with IOChecker with BeforeAndAfterAll {
  private val db = Values.db
  val transactor: doobie.Transactor[IO] = db.xa
  private val user = User(User.Id.generate(), "John", "Doe", Email("john@mail.com"), Instant.now(), Instant.now())

  override def beforeAll(): Unit = db.createTables().unsafeRunSync()

  override def afterAll(): Unit = db.dropTables().unsafeRunSync()

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
