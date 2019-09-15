package fr.gospeak.infra.services.storage.sql

import fr.gospeak.core.domain.UserRequest
import fr.gospeak.core.domain.UserRequest._
import fr.gospeak.infra.services.storage.sql.UserRequestRepoSqlSpec._
import fr.gospeak.infra.services.storage.sql.testingutils.RepoSpec

class UserRequestRepoSqlSpec extends RepoSpec {
  describe("UserRequestRepoSql") {
    describe("Queries") {
      it("should build selectOnePending for group") {
        val q = UserRequestRepoSql.selectOnePending(group.id, userRequest.id, now)
        q.sql shouldBe s"SELECT $fields FROM $table WHERE id=? AND group_id=? AND accepted IS NULL AND rejected IS NULL AND (deadline IS NULL OR deadline > ?)"
        check(q)
      }
      it("should build selectPage for user") {
        val (s, c) = UserRequestRepoSql.selectPage(user.id, params)
        s.sql shouldBe s"SELECT $fields FROM $table WHERE created_by=? ORDER BY created IS NULL, created DESC OFFSET 0 LIMIT 20"
        c.sql shouldBe s"SELECT count(*) FROM $table WHERE created_by=? "
        check(s)
        check(c)
      }
      it("should build selectAllPending for group") {
        val q = UserRequestRepoSql.selectAllPending(group.id, now)
        q.sql shouldBe s"SELECT $fields FROM $table WHERE group_id=? AND accepted IS NULL AND rejected IS NULL AND (deadline IS NULL OR deadline > ?)"
        check(q)
      }
      describe("AccountValidation") {
        val req = AccountValidationRequest(UserRequest.Id.generate(), user.email, now, now, user.id, None)
        it("should build insert") {
          val q = UserRequestRepoSql.AccountValidation.insert(req)
          q.sql shouldBe s"INSERT INTO $table (id, kind, email, deadline, created, created_by, accepted) VALUES (?, ?, ?, ?, ?, ?, ?)"
          check(q)
        }
        it("should build accept") {
          val q = UserRequestRepoSql.AccountValidation.accept(req.id, now)
          q.sql shouldBe s"UPDATE $table SET accepted=? WHERE id=? AND kind=? AND deadline > ? AND accepted IS NULL"
          check(q)
        }
        it("should build selectPending with id") {
          val q = UserRequestRepoSql.AccountValidation.selectPending(req.id, now)
          q.sql shouldBe s"SELECT id, email, deadline, created, created_by, accepted FROM $table WHERE id=? AND kind=? AND deadline > ? AND accepted IS NULL"
          // check(q) // ignored because of missing "NOT NULL" due to sealed trait
        }
        it("should build selectPending with user") {
          val q = UserRequestRepoSql.AccountValidation.selectPending(user.id, now)
          q.sql shouldBe s"SELECT id, email, deadline, created, created_by, accepted FROM $table WHERE created_by=? AND kind=? AND deadline > ? AND accepted IS NULL"
          // check(q) // ignored because of missing "NOT NULL" due to sealed trait
        }
      }
      describe("ResetPassword") {
        val req = PasswordResetRequest(UserRequest.Id.generate(), user.email, now, now, None)
        it("should build insert") {
          val q = UserRequestRepoSql.PasswordReset.insert(req)
          q.sql shouldBe s"INSERT INTO $table (id, kind, email, deadline, created, accepted) VALUES (?, ?, ?, ?, ?, ?)"
          check(q)
        }
        it("should build accept") {
          val q = UserRequestRepoSql.PasswordReset.accept(req.id, now)
          q.sql shouldBe s"UPDATE $table SET accepted=? WHERE id=? AND kind=? AND deadline > ? AND accepted IS NULL"
          check(q)
        }
        it("should build selectPending with id") {
          val q = UserRequestRepoSql.PasswordReset.selectPending(req.id, now)
          q.sql shouldBe s"SELECT id, email, deadline, created, accepted FROM $table WHERE id=? AND kind=? AND deadline > ? AND accepted IS NULL"
          // check(q) // ignored because of missing "NOT NULL" due to sealed trait
        }
        it("should build selectPending with email") {
          val q = UserRequestRepoSql.PasswordReset.selectPending(user.email, now)
          q.sql shouldBe s"SELECT id, email, deadline, created, accepted FROM $table WHERE email=? AND kind=? AND deadline > ? AND accepted IS NULL"
          // check(q) // ignored because of missing "NOT NULL" due to sealed trait
        }
      }
      describe("UserAskToJoinAGroup") {
        val req = UserAskToJoinAGroupRequest(UserRequest.Id.generate(), group.id, now, user.id, None, None, None)
        it("should build insert") {
          val q = UserRequestRepoSql.UserAskToJoinAGroup.insert(req)
          q.sql shouldBe s"INSERT INTO $table (id, kind, group_id, created, created_by, accepted, accepted_by, rejected, rejected_by, canceled, canceled_by) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)"
          check(q)
        }
        it("should build accept") {
          val q = UserRequestRepoSql.UserAskToJoinAGroup.accept(req.id, user.id, now)
          q.sql shouldBe s"UPDATE $table SET accepted=?, accepted_by=? WHERE id=? AND kind=? AND accepted IS NULL AND rejected IS NULL AND canceled IS NULL"
          check(q)
        }
        it("should build reject") {
          val q = UserRequestRepoSql.UserAskToJoinAGroup.reject(req.id, user.id, now)
          q.sql shouldBe s"UPDATE $table SET rejected=?, rejected_by=? WHERE id=? AND kind=? AND accepted IS NULL AND rejected IS NULL AND canceled IS NULL"
          check(q)
        }
        it("should build cancel") {
          val q = UserRequestRepoSql.UserAskToJoinAGroup.cancel(req.id, user.id, now)
          q.sql shouldBe s"UPDATE $table SET canceled=?, canceled_by=? WHERE id=? AND kind=? AND accepted IS NULL AND rejected IS NULL AND canceled IS NULL"
          check(q)
        }
        it("should build selectOnePending") {
          val q = UserRequestRepoSql.UserAskToJoinAGroup.selectOnePending(group.id, userRequest.id)
          q.sql shouldBe s"SELECT id, group_id, created, created_by, accepted, accepted_by, rejected, rejected_by, canceled, canceled_by FROM $table WHERE id=? AND kind=? AND group_id=? AND accepted IS NULL AND rejected IS NULL AND canceled IS NULL "
          // check(q) // ignored because missing "NOT NULL" on 'group_id' and 'created_by'... due to sealed trait
        }
        it("should build selectAllPending") {
          val q = UserRequestRepoSql.UserAskToJoinAGroup.selectAllPending(user.id)
          q.sql shouldBe s"SELECT id, group_id, created, created_by, accepted, accepted_by, rejected, rejected_by, canceled, canceled_by FROM $table WHERE kind=? AND created_by=? AND accepted IS NULL AND rejected IS NULL AND canceled IS NULL "
          // check(q) // ignored because missing "NOT NULL" on 'group_id' and 'created_by'... due to sealed trait
        }
      }
      describe("GroupInviteQueries") {
        val req = GroupInvite(UserRequest.Id.generate(), group.id, user.email, now, user.id, None, None, None)
        it("should build insert") {
          val q = UserRequestRepoSql.GroupInviteQueries.insert(req)
          q.sql shouldBe s"INSERT INTO $table (id, kind, group_id, email, created, created_by, accepted, accepted_by, rejected, rejected_by, canceled, canceled_by) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)"
          check(q)
        }
        it("should build accept") {
          val q = UserRequestRepoSql.GroupInviteQueries.accept(req.id, user.id, now)
          q.sql shouldBe s"UPDATE $table SET accepted=?, accepted_by=? WHERE id=? AND kind=? AND accepted IS NULL AND rejected IS NULL AND canceled IS NULL"
          check(q)
        }
        it("should build reject") {
          val q = UserRequestRepoSql.GroupInviteQueries.reject(req.id, user.id, now)
          q.sql shouldBe s"UPDATE $table SET rejected=?, rejected_by=? WHERE id=? AND kind=? AND accepted IS NULL AND rejected IS NULL AND canceled IS NULL"
          check(q)
        }
        it("should build cancel") {
          val q = UserRequestRepoSql.GroupInviteQueries.cancel(req.id, user.id, now)
          q.sql shouldBe s"UPDATE $table SET canceled=?, canceled_by=? WHERE id=? AND kind=? AND accepted IS NULL AND rejected IS NULL AND canceled IS NULL"
          check(q)
        }
        it("should build selectOne") {
          val q = UserRequestRepoSql.GroupInviteQueries.selectOne(req.id)
          q.sql shouldBe s"SELECT id, group_id, email, created, created_by, accepted, accepted_by, rejected, rejected_by, canceled, canceled_by FROM $table WHERE id=? "
          // check(q) // ignored because missing "NOT NULL" on 'group_id', 'email' and 'created_by'... due to sealed trait
        }
        it("should build selectAllPending") {
          val q = UserRequestRepoSql.GroupInviteQueries.selectAllPending(group.id)
          q.sql shouldBe s"SELECT id, group_id, email, created, created_by, accepted, accepted_by, rejected, rejected_by, canceled, canceled_by FROM $table WHERE kind=? AND group_id=? AND accepted IS NULL AND rejected IS NULL AND canceled IS NULL "
          // check(q) // ignored because missing "NOT NULL" on 'group_id', 'email' and 'created_by'... due to sealed trait
        }
      }
      describe("TalkInviteQueries") {
        val req = TalkInvite(UserRequest.Id.generate(), talk.id, user.email, now, user.id, None, None, None)
        it("should build insert") {
          val q = UserRequestRepoSql.TalkInviteQueries.insert(req)
          q.sql shouldBe s"INSERT INTO $table (id, kind, talk_id, email, created, created_by, accepted, accepted_by, rejected, rejected_by, canceled, canceled_by) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)"
          check(q)
        }
        it("should build accept") {
          val q = UserRequestRepoSql.TalkInviteQueries.accept(req.id, user.id, now)
          q.sql shouldBe s"UPDATE $table SET accepted=?, accepted_by=? WHERE id=? AND kind=? AND accepted IS NULL AND rejected IS NULL AND canceled IS NULL"
          check(q)
        }
        it("should build reject") {
          val q = UserRequestRepoSql.TalkInviteQueries.reject(req.id, user.id, now)
          q.sql shouldBe s"UPDATE $table SET rejected=?, rejected_by=? WHERE id=? AND kind=? AND accepted IS NULL AND rejected IS NULL AND canceled IS NULL"
          check(q)
        }
        it("should build cancel") {
          val q = UserRequestRepoSql.TalkInviteQueries.cancel(req.id, user.id, now)
          q.sql shouldBe s"UPDATE $table SET canceled=?, canceled_by=? WHERE id=? AND kind=? AND accepted IS NULL AND rejected IS NULL AND canceled IS NULL"
          check(q)
        }
        it("should build selectOne") {
          val q = UserRequestRepoSql.TalkInviteQueries.selectOne(req.id)
          q.sql shouldBe s"SELECT id, talk_id, email, created, created_by, accepted, accepted_by, rejected, rejected_by, canceled, canceled_by FROM $table WHERE id=? "
          // check(q) // ignored because missing "NOT NULL" on 'talk_id', 'email' and 'created_by'... due to sealed trait
        }
        it("should build selectAllPending") {
          val q = UserRequestRepoSql.TalkInviteQueries.selectAllPending(talk.id)
          q.sql shouldBe s"SELECT id, talk_id, email, created, created_by, accepted, accepted_by, rejected, rejected_by, canceled, canceled_by FROM $table WHERE kind=? AND talk_id=? AND accepted IS NULL AND rejected IS NULL AND canceled IS NULL "
          // check(q) // ignored because missing "NOT NULL" on 'talk_id', 'email' and 'created_by'... due to sealed trait
        }
      }
      describe("ProposalInviteQueries") {
        val req = ProposalInvite(UserRequest.Id.generate(), proposal.id, user.email, now, user.id, None, None, None)
        it("should build insert") {
          val q = UserRequestRepoSql.ProposalInviteQueries.insert(req)
          q.sql shouldBe s"INSERT INTO $table (id, kind, proposal_id, email, created, created_by, accepted, accepted_by, rejected, rejected_by, canceled, canceled_by) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)"
          check(q)
        }
        it("should build accept") {
          val q = UserRequestRepoSql.ProposalInviteQueries.accept(req.id, user.id, now)
          q.sql shouldBe s"UPDATE $table SET accepted=?, accepted_by=? WHERE id=? AND kind=? AND accepted IS NULL AND rejected IS NULL AND canceled IS NULL"
          check(q)
        }
        it("should build reject") {
          val q = UserRequestRepoSql.ProposalInviteQueries.reject(req.id, user.id, now)
          q.sql shouldBe s"UPDATE $table SET rejected=?, rejected_by=? WHERE id=? AND kind=? AND accepted IS NULL AND rejected IS NULL AND canceled IS NULL"
          check(q)
        }
        it("should build cancel") {
          val q = UserRequestRepoSql.ProposalInviteQueries.cancel(req.id, user.id, now)
          q.sql shouldBe s"UPDATE $table SET canceled=?, canceled_by=? WHERE id=? AND kind=? AND accepted IS NULL AND rejected IS NULL AND canceled IS NULL"
          check(q)
        }
        it("should build selectOne") {
          val q = UserRequestRepoSql.ProposalInviteQueries.selectOne(req.id)
          q.sql shouldBe s"SELECT id, proposal_id, email, created, created_by, accepted, accepted_by, rejected, rejected_by, canceled, canceled_by FROM $table WHERE id=? "
          // check(q) // ignored because missing "NOT NULL" on 'proposal_id', 'email' and 'created_by'... due to sealed trait
        }
        it("should build selectAllPending") {
          val q = UserRequestRepoSql.ProposalInviteQueries.selectAllPending(proposal.id)
          q.sql shouldBe s"SELECT id, proposal_id, email, created, created_by, accepted, accepted_by, rejected, rejected_by, canceled, canceled_by FROM $table WHERE kind=? AND proposal_id=? AND accepted IS NULL AND rejected IS NULL AND canceled IS NULL "
          // check(q) // ignored because missing "NOT NULL" on 'proposal_id', 'email' and 'created_by'... due to sealed trait
        }
      }
    }
  }
}

object UserRequestRepoSqlSpec {
  val table = "requests"
  val fields = "id, kind, group_id, talk_id, proposal_id, email, deadline, created, created_by, accepted, accepted_by, rejected, rejected_by, canceled, canceled_by"
}
