package fr.gospeak.infra.services.storage.sql

import fr.gospeak.core.domain.UserRequest
import fr.gospeak.core.domain.UserRequest._
import fr.gospeak.infra.services.storage.sql.UserRequestRepoSql._
import fr.gospeak.infra.services.storage.sql.UserRequestRepoSqlSpec._
import fr.gospeak.infra.services.storage.sql.testingutils.RepoSpec

class UserRequestRepoSqlSpec extends RepoSpec {
  describe("UserRequestRepoSql") {
    describe("Queries") {
      it("should build selectPage for user") {
        val (s, c) = selectPage(user.id, params)
        s.sql shouldBe s"SELECT $fieldList FROM requests WHERE created_by = ? ORDER BY created IS NULL, created DESC OFFSET 0 LIMIT 20"
        c.sql shouldBe "SELECT count(*) FROM requests WHERE created_by = ? "
        check(s)
        check(c)
      }
      it("should build selectPage for group") {
        val (s, c) = selectPage(group.id, params)
        s.sql shouldBe s"SELECT $fieldList FROM requests WHERE group_id = ? ORDER BY created IS NULL, created DESC OFFSET 0 LIMIT 20"
        c.sql shouldBe "SELECT count(*) FROM requests WHERE group_id = ? "
        check(s)
        check(c)
      }
      describe("AccountValidation") {
        import AccountValidation._
        val req = AccountValidationRequest(UserRequest.Id.generate(), user.email, now, now, user.id, None)
        it("should build insert") {
          val q = insert(req)
          q.sql shouldBe "INSERT INTO requests (id, kind, email, deadline, created, created_by, accepted) VALUES (?, ?, ?, ?, ?, ?, ?)"
          check(q)
        }
        it("should build accept") {
          val q = accept(req.id, now)
          q.sql shouldBe "UPDATE requests SET accepted=? WHERE id=? AND kind=? AND deadline > ? AND accepted IS NULL"
          check(q)
        }
        it("should build selectPending with id") {
          val q = selectPending(req.id, now)
          q.sql shouldBe "SELECT id, email, deadline, created, created_by, accepted FROM requests WHERE id=? AND kind=? AND deadline > ? AND accepted IS NULL"
          // check(q) // ignored because of missing "NOT NULL" due to sealed trait
        }
        it("should build selectPending with user") {
          val q = selectPending(user.id, now)
          q.sql shouldBe "SELECT id, email, deadline, created, created_by, accepted FROM requests WHERE created_by=? AND kind=? AND deadline > ? AND accepted IS NULL"
          // check(q) // ignored because of missing "NOT NULL" due to sealed trait
        }
      }
      describe("ResetPassword") {
        import PasswordReset._
        val req = PasswordResetRequest(UserRequest.Id.generate(), user.email, now, now, None)
        it("should build insert") {
          val q = insert(req)
          q.sql shouldBe "INSERT INTO requests (id, kind, email, deadline, created, accepted) VALUES (?, ?, ?, ?, ?, ?)"
          check(q)
        }
        it("should build accept") {
          val q = accept(req.id, now)
          q.sql shouldBe "UPDATE requests SET accepted=? WHERE id=? AND kind=? AND deadline > ? AND accepted IS NULL"
          check(q)
        }
        it("should build selectPending with id") {
          val q = selectPending(req.id, now)
          q.sql shouldBe "SELECT id, email, deadline, created, accepted FROM requests WHERE id=? AND kind=? AND deadline > ? AND accepted IS NULL"
          // check(q) // ignored because of missing "NOT NULL" due to sealed trait
        }
        it("should build selectPending with email") {
          val q = selectPending(user.email, now)
          q.sql shouldBe "SELECT id, email, deadline, created, accepted FROM requests WHERE email=? AND kind=? AND deadline > ? AND accepted IS NULL"
          // check(q) // ignored because of missing "NOT NULL" due to sealed trait
        }
      }
      describe("UserAskToJoinAGroup") {
        import UserAskToJoinAGroup._
        val req = UserAskToJoinAGroupRequest(UserRequest.Id.generate(), group.id, now, user.id, None, None)
        it("should build insert") {
          val q = insert(req)
          q.sql shouldBe "INSERT INTO requests (id, kind, group_id, created, created_by, accepted, accepted_by, rejected, rejected_by) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)"
          check(q)
        }
        it("should build accept") {
          val q = accept(req.id, user.id, now)
          q.sql shouldBe "UPDATE requests SET accepted=?, accepted_by=? WHERE id=? AND kind=? AND accepted IS NULL AND rejected IS NULL"
          check(q)
        }
        it("should build reject") {
          val q = reject(req.id, user.id, now)
          q.sql shouldBe "UPDATE requests SET rejected=?, rejected_by=? WHERE id=? AND kind=? AND accepted IS NULL AND rejected IS NULL"
          check(q)
        }
        it("should build selectAllPending") {
          val q = selectAllPending(user.id)
          q.sql shouldBe "SELECT id, group_id, created, created_by, accepted, accepted_by, rejected, rejected_by FROM requests WHERE kind=? AND created_by=? AND accepted IS NULL AND rejected IS NULL "
          // check(q) // ignored because missing "NOT NULL" on 'group_id' and 'created_by'... due to sealed trait
        }
      }
    }
  }
}

object UserRequestRepoSqlSpec {
  val fieldList = "id, kind, email, group_id, deadline, created, created_by, accepted, accepted_by, rejected, rejected_by"
}
