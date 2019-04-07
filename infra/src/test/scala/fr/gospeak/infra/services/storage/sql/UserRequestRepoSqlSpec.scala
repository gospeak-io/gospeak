package fr.gospeak.infra.services.storage.sql

import fr.gospeak.core.domain.UserRequest
import fr.gospeak.core.domain.UserRequest._
import fr.gospeak.infra.services.storage.sql.UserRequestRepoSql._
import fr.gospeak.infra.services.storage.sql.testingutils.RepoSpec

class UserRequestRepoSqlSpec extends RepoSpec {
  describe("UserRequestRepoSql") {
    describe("Queries") {
      describe("AccountValidation") {
        import AccountValidation._
        val accountValidationRequest = AccountValidationRequest(UserRequest.Id.generate(), user.email, user.id, now, now, None)
        it("should build insert") {
          val q = insert(accountValidationRequest)
          q.sql shouldBe "INSERT INTO requests (id, kind, email, user_id, deadline, created, accepted) VALUES (?, ?, ?, ?, ?, ?, ?)"
          check(q)
        }
        it("should build accept") {
          val q = accept(accountValidationRequest.id, now)
          q.sql shouldBe "UPDATE requests SET accepted=? WHERE id=? AND kind=? AND deadline > ? AND accepted IS NULL"
          check(q)
        }
        it("should build selectPending with id") {
          val q = selectPending(accountValidationRequest.id, now)
          q.sql shouldBe "SELECT id, email, user_id, deadline, created, accepted FROM requests WHERE id=? AND kind=? AND deadline > ? AND accepted IS NULL"
          // check(q) // ignored because of missing "NOT NULL" due to sealed trait
        }
        it("should build selectPending with user") {
          val q = selectPending(user.id, now)
          q.sql shouldBe "SELECT id, email, user_id, deadline, created, accepted FROM requests WHERE user_id=? AND kind=? AND deadline > ? AND accepted IS NULL"
          // check(q) // ignored because of missing "NOT NULL" due to sealed trait
        }
      }
      describe("ResetPassword") {
        import ResetPassword._
        val passwordResetRequest = PasswordResetRequest(UserRequest.Id.generate(), user.email, now, now, None)
        it("should build insert") {
          val q = insert(passwordResetRequest)
          q.sql shouldBe "INSERT INTO requests (id, kind, email, deadline, created, accepted) VALUES (?, ?, ?, ?, ?, ?)"
          check(q)
        }
        it("should build accept") {
          val q = accept(passwordResetRequest.id, now)
          q.sql shouldBe "UPDATE requests SET accepted=? WHERE id=? AND kind=? AND deadline > ? AND accepted IS NULL"
          check(q)
        }
        it("should build selectPending with id") {
          val q = selectPending(passwordResetRequest.id, now)
          q.sql shouldBe "SELECT id, email, deadline, created, accepted FROM requests WHERE id=? AND kind=? AND deadline > ? AND accepted IS NULL"
          // check(q) // ignored because of missing "NOT NULL" due to sealed trait
        }
        it("should build selectPending with email") {
          val q = selectPending(user.email, now)
          q.sql shouldBe "SELECT id, email, deadline, created, accepted FROM requests WHERE email=? AND kind=? AND deadline > ? AND accepted IS NULL"
          // check(q) // ignored because of missing "NOT NULL" due to sealed trait
        }
      }
    }
  }
}
