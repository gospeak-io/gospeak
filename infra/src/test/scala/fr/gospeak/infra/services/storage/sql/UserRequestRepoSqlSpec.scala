package fr.gospeak.infra.services.storage.sql

import fr.gospeak.core.domain.UserRequest
import fr.gospeak.core.domain.UserRequest._
import fr.gospeak.infra.services.storage.sql.UserRequestRepoSql._
import fr.gospeak.infra.services.storage.sql.UserRequestRepoSqlSpec._
import fr.gospeak.infra.services.storage.sql.testingutils.RepoSpec

class UserRequestRepoSqlSpec extends RepoSpec {
  describe("UserRequestRepoSql") {
    describe("Queries") {
      it("should build selectOnePending for group") {
        val q = selectOnePending(group.id, userRequest.id, now)
        q.sql shouldBe s"SELECT $fieldList FROM requests WHERE id=? AND group_id=? AND accepted IS NULL AND rejected IS NULL AND (deadline IS NULL OR deadline > ?)"
        check(q)
      }
      it("should build selectPage for user") {
        val (s, c) = selectPage(user.id, params)
        s.sql shouldBe s"SELECT $fieldList FROM requests WHERE created_by=? ORDER BY created IS NULL, created DESC OFFSET 0 LIMIT 20"
        c.sql shouldBe "SELECT count(*) FROM requests WHERE created_by=? "
        check(s)
        check(c)
      }
      it("should build selectAllPending for group") {
        val q = selectAllPending(group.id, now)
        q.sql shouldBe s"SELECT $fieldList FROM requests WHERE group_id=? AND accepted IS NULL AND rejected IS NULL AND (deadline IS NULL OR deadline > ?)"
        check(q)
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
        val req = UserAskToJoinAGroupRequest(UserRequest.Id.generate(), group.id, now, user.id, None, None, None)
        it("should build insert") {
          val q = insert(req)
          q.sql shouldBe "INSERT INTO requests (id, kind, group_id, created, created_by, accepted, accepted_by, rejected, rejected_by, canceled, canceled_by) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)"
          check(q)
        }
        it("should build accept") {
          val q = accept(req.id, user.id, now)
          q.sql shouldBe "UPDATE requests SET accepted=?, accepted_by=? WHERE id=? AND kind=? AND accepted IS NULL AND rejected IS NULL AND canceled IS NULL"
          check(q)
        }
        it("should build reject") {
          val q = reject(req.id, user.id, now)
          q.sql shouldBe "UPDATE requests SET rejected=?, rejected_by=? WHERE id=? AND kind=? AND accepted IS NULL AND rejected IS NULL AND canceled IS NULL"
          check(q)
        }
        it("should build cancel") {
          val q = UserAskToJoinAGroup.cancel(req.id, user.id, now)
          q.sql shouldBe "UPDATE requests SET canceled=?, canceled_by=? WHERE id=? AND kind=? AND accepted IS NULL AND rejected IS NULL AND canceled IS NULL"
          check(q)
        }
        it("should build selectOnePending") {
          val q = selectOnePending(group.id, userRequest.id)
          q.sql shouldBe "SELECT id, group_id, created, created_by, accepted, accepted_by, rejected, rejected_by, canceled, canceled_by FROM requests WHERE id=? AND kind=? AND group_id=? AND accepted IS NULL AND rejected IS NULL AND canceled IS NULL "
          // check(q) // ignored because missing "NOT NULL" on 'group_id' and 'created_by'... due to sealed trait
        }
        it("should build selectAllPending") {
          val q = selectAllPending(user.id)
          q.sql shouldBe "SELECT id, group_id, created, created_by, accepted, accepted_by, rejected, rejected_by, canceled, canceled_by FROM requests WHERE kind=? AND created_by=? AND accepted IS NULL AND rejected IS NULL AND canceled IS NULL "
          // check(q) // ignored because missing "NOT NULL" on 'group_id' and 'created_by'... due to sealed trait
        }
      }
      describe("TalkInviteQueries") {
        import TalkInviteQueries._
        val req = TalkInvite(UserRequest.Id.generate(), talk.id, user.email, now, user.id, None, None, None)
        it("should build insert") {
          val q = insert(req)
          q.sql shouldBe "INSERT INTO requests (id, kind, talk_id, email, created, created_by, accepted, accepted_by, rejected, rejected_by, canceled, canceled_by) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)"
          check(q)
        }
        it("should build accept") {
          val q = accept(req.id, user.id, now)
          q.sql shouldBe "UPDATE requests SET accepted=?, accepted_by=? WHERE id=? AND kind=? AND accepted IS NULL AND rejected IS NULL AND canceled IS NULL"
          check(q)
        }
        it("should build reject") {
          val q = reject(req.id, user.id, now)
          q.sql shouldBe "UPDATE requests SET rejected=?, rejected_by=? WHERE id=? AND kind=? AND accepted IS NULL AND rejected IS NULL AND canceled IS NULL"
          check(q)
        }
        it("should build cancel") {
          val q = TalkInviteQueries.cancel(req.id, user.id, now)
          q.sql shouldBe "UPDATE requests SET canceled=?, canceled_by=? WHERE id=? AND kind=? AND accepted IS NULL AND rejected IS NULL AND canceled IS NULL"
          check(q)
        }
        it("should build selectOne") {
          val q = selectOne(req.id)
          q.sql shouldBe "SELECT id, talk_id, email, created, created_by, accepted, accepted_by, rejected, rejected_by, canceled, canceled_by FROM requests WHERE id=? "
          // check(q) // ignored because missing "NOT NULL" on 'talk_id', 'email' and 'created_by'... due to sealed trait
        }
        it("should build selectOneWithTalk") {
          val q = selectOneWithTalk(req.id)
          q.sql shouldBe "SELECT i.id, i.talk_id, i.email, i.created, i.created_by, i.accepted, i.accepted_by, i.rejected, i.rejected_by, i.canceled, i.canceled_by, t.id, t.slug, t.status, t.title, t.duration, t.description, t.speakers, t.slides, t.video, t.tags, t.created, t.created_by, t.updated, t.updated_by FROM requests i INNER JOIN talks t ON i.talk_id=t.id WHERE i.id=? "
          // check(q) // ignored because missing "NOT NULL" on 'talk_id', 'email' and 'created_by'... due to sealed trait
        }
        it("should build selectOnePending") {
          val q = selectOnePending(req.id)
          q.sql shouldBe "SELECT id, talk_id, email, created, created_by, accepted, accepted_by, rejected, rejected_by, canceled, canceled_by FROM requests WHERE id=? AND kind=? AND accepted IS NULL AND rejected IS NULL AND canceled IS NULL "
          // check(q) // ignored because missing "NOT NULL" on 'talk_id', 'email' and 'created_by'... due to sealed trait
        }
        it("should build selectAllPending") {
          val q = selectAllPending(talk.id)
          q.sql shouldBe "SELECT id, talk_id, email, created, created_by, accepted, accepted_by, rejected, rejected_by, canceled, canceled_by FROM requests WHERE kind=? AND accepted IS NULL AND rejected IS NULL AND canceled IS NULL "
          // check(q) // ignored because missing "NOT NULL" on 'talk_id', 'email' and 'created_by'... due to sealed trait
        }
      }
    }
  }
}

object UserRequestRepoSqlSpec {
  val fieldList = "id, kind, group_id, talk_id, proposal_id, email, deadline, created, created_by, accepted, accepted_by, rejected, rejected_by, canceled, canceled_by"
}
