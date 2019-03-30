package fr.gospeak.infra.services.storage.sql.tables

import fr.gospeak.core.domain.UserRequest
import fr.gospeak.core.domain.UserRequest._
import fr.gospeak.infra.services.storage.sql.tables.UserRequestTable._
import fr.gospeak.infra.services.storage.sql.tables.testingutils.TableSpec

class UserRequestTableSpec extends TableSpec {
  private val emailValidationRequest = AccountValidationRequest(UserRequest.Id.generate(), user.email, user.id, now, now, None)

  describe("UserRequestTable") {
    describe("EmailValidation") {
      it("should build insert query") {
        val q = AccountValidation.insert(emailValidationRequest)
        q.sql shouldBe "INSERT INTO requests (id, kind, email, user_id, deadline, created, accepted) VALUES (?, ?, ?, ?, ?, ?, ?)"
        check(q)
      }
      it("should build selectOne query") {
        val q = AccountValidation.selectPending(emailValidationRequest.id, now)
        q.sql shouldBe "SELECT id, email, user_id, deadline, created, accepted FROM requests WHERE id=? AND kind=? AND deadline > ? AND accepted IS NULL"
        // check(q) // ignored because of missing "NOT NULL" due to sealed trait
      }
      it("should build accept query") {
        val q = AccountValidation.validate(emailValidationRequest.id, now)
        q.sql shouldBe "UPDATE requests SET accepted=? WHERE id=? AND kind=? AND deadline > ? AND accepted IS NULL"
        check(q)
      }
    }
  }
}
