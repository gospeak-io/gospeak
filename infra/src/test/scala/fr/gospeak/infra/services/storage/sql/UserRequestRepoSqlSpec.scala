package fr.gospeak.infra.services.storage.sql

import fr.gospeak.core.domain.UserRequest
import fr.gospeak.core.domain.UserRequest._
import fr.gospeak.infra.services.storage.sql.UserRequestRepoSqlSpec._
import fr.gospeak.infra.services.storage.sql.testingutils.RepoSpec
import fr.gospeak.infra.services.storage.sql.testingutils.RepoSpec.mapFields

class UserRequestRepoSqlSpec extends RepoSpec {
  describe("UserRequestRepoSql") {
    describe("Queries") {
      it("should build selectOne") {
        val q = UserRequestRepoSql.selectOne(userRequest.id)
        check(q, s"SELECT $fields FROM $table WHERE ur.id=? $orderBy")
      }
      it("should build selectOnePending for group") {
        val q = UserRequestRepoSql.selectOnePending(group.id, userRequest.id, now)
        check(q, s"SELECT $fields FROM $table WHERE ur.id=? AND ur.group_id=? AND ur.accepted_at IS NULL AND ur.rejected_at IS NULL AND (ur.deadline IS NULL OR ur.deadline > ?) $orderBy")
      }
      it("should build selectPage for user") {
        val q = UserRequestRepoSql.selectPage(user.id, params)
        check(q, s"SELECT $fields FROM $table WHERE ur.created_by=? $orderBy LIMIT 20 OFFSET 0")
      }
      it("should build selectAllPending for group") {
        val q = UserRequestRepoSql.selectAllPending(group.id, now)
        check(q, s"SELECT $fields FROM $table WHERE ur.group_id=? AND ur.accepted_at IS NULL AND ur.rejected_at IS NULL AND (ur.deadline IS NULL OR ur.deadline > ?) $orderBy")
      }
      describe("AccountValidation") {
        val req = AccountValidationRequest(UserRequest.Id.generate(), user.email, now, now, user.id, None)
        it("should build insert") {
          val q = UserRequestRepoSql.AccountValidation.insert(req)
          check(q, s"INSERT INTO ${table.replaceAll(" ur", "")} (id, kind, email, deadline, created_at, created_by, accepted_at) VALUES (?, ?, ?, ?, ?, ?, ?)")
        }
        it("should build accept") {
          val q = UserRequestRepoSql.AccountValidation.accept(req.id, now)
          check(q, s"UPDATE $table SET accepted_at=? WHERE ur.id=? AND ur.kind=? AND ur.deadline > ? AND ur.accepted_at IS NULL")
        }
        it("should build selectOne with id") {
          val q = UserRequestRepoSql.AccountValidation.selectOne(req.id)
          q.fr.update.sql shouldBe s"SELECT $accountValidationFields FROM $table WHERE ur.id=? AND ur.kind=? $orderBy"
          // check(q) // ignored because of missing "NOT NULL" due to sealed trait
        }
        it("should build selectPending with user") {
          val q = UserRequestRepoSql.AccountValidation.selectPending(user.id, now)
          q.fr.update.sql shouldBe s"SELECT $accountValidationFields FROM $table WHERE ur.created_by=? AND ur.kind=? AND ur.deadline > ? AND ur.accepted_at IS NULL $orderBy"
          // check(q) // ignored because of missing "NOT NULL" due to sealed trait
        }
      }
      describe("ResetPassword") {
        val req = PasswordResetRequest(UserRequest.Id.generate(), user.email, now, now, None)
        it("should build insert") {
          val q = UserRequestRepoSql.PasswordReset.insert(req)
          check(q, s"INSERT INTO ${table.replaceAll(" ur", "")} (id, kind, email, deadline, created_at, accepted_at) VALUES (?, ?, ?, ?, ?, ?)")
        }
        it("should build accept") {
          val q = UserRequestRepoSql.PasswordReset.accept(req.id, now)
          check(q, s"UPDATE $table SET accepted_at=? WHERE ur.id=? AND ur.kind=? AND ur.deadline > ? AND ur.accepted_at IS NULL")
        }
        it("should build selectPending with id") {
          val q = UserRequestRepoSql.PasswordReset.selectPending(req.id, now)
          q.fr.update.sql shouldBe s"SELECT $resetPasswordFields FROM $table WHERE ur.id=? AND ur.kind=? AND ur.deadline > ? AND ur.accepted_at IS NULL $orderBy"
          // check(q) // ignored because of missing "NOT NULL" due to sealed trait
        }
        it("should build selectPending with email") {
          val q = UserRequestRepoSql.PasswordReset.selectPending(user.email, now)
          q.fr.update.sql shouldBe s"SELECT $resetPasswordFields FROM $table WHERE ur.email=? AND ur.kind=? AND ur.deadline > ? AND ur.accepted_at IS NULL $orderBy"
          // check(q) // ignored because of missing "NOT NULL" due to sealed trait
        }
      }
      describe("UserAskToJoinAGroup") {
        val req = UserAskToJoinAGroupRequest(UserRequest.Id.generate(), group.id, now, user.id, None, None, None)
        it("should build insert") {
          val q = UserRequestRepoSql.UserAskToJoinAGroup.insert(req)
          check(q, s"INSERT INTO ${table.replaceAll(" ur", "")} (id, kind, group_id, created_at, created_by, accepted_at, accepted_by, rejected_at, rejected_by, canceled_at, canceled_by) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)")
        }
        it("should build accept") {
          val q = UserRequestRepoSql.UserAskToJoinAGroup.accept(req.id, user.id, now)
          check(q, s"UPDATE $table SET accepted_at=?, accepted_by=? WHERE ur.id=? AND ur.kind=? AND ur.accepted_at IS NULL AND ur.rejected_at IS NULL AND ur.canceled_at IS NULL")
        }
        it("should build reject") {
          val q = UserRequestRepoSql.UserAskToJoinAGroup.reject(req.id, user.id, now)
          check(q, s"UPDATE $table SET rejected_at=?, rejected_by=? WHERE ur.id=? AND ur.kind=? AND ur.accepted_at IS NULL AND ur.rejected_at IS NULL AND ur.canceled_at IS NULL")
        }
        it("should build cancel") {
          val q = UserRequestRepoSql.UserAskToJoinAGroup.cancel(req.id, user.id, now)
          check(q, s"UPDATE $table SET canceled_at=?, canceled_by=? WHERE ur.id=? AND ur.kind=? AND ur.accepted_at IS NULL AND ur.rejected_at IS NULL AND ur.canceled_at IS NULL")
        }
        it("should build selectOnePending") {
          val q = UserRequestRepoSql.UserAskToJoinAGroup.selectOnePending(group.id, userRequest.id)
          q.fr.update.sql shouldBe s"SELECT $userAskToJoinAGroupFields FROM $table WHERE ur.id=? AND ur.kind=? AND ur.group_id=? AND ur.accepted_at IS NULL AND ur.rejected_at IS NULL AND ur.canceled_at IS NULL $orderBy"
          // check(q) // ignored because missing "NOT NULL" on 'group_id' and 'created_by'... due to sealed trait
        }
        it("should build selectAllPending") {
          val q = UserRequestRepoSql.UserAskToJoinAGroup.selectAllPending(user.id)
          q.fr.update.sql shouldBe s"SELECT $userAskToJoinAGroupFields FROM $table WHERE ur.kind=? AND ur.created_by=? AND ur.accepted_at IS NULL AND ur.rejected_at IS NULL AND ur.canceled_at IS NULL $orderBy"
          // check(q) // ignored because missing "NOT NULL" on 'group_id' and 'created_by'... due to sealed trait
        }
      }
      describe("GroupInviteQueries") {
        val req = GroupInvite(UserRequest.Id.generate(), group.id, user.email, now, user.id, None, None, None)
        it("should build insert") {
          val q = UserRequestRepoSql.GroupInviteQueries.insert(req)
          check(q, s"INSERT INTO ${table.replaceAll(" ur", "")} (id, kind, group_id, email, created_at, created_by, accepted_at, accepted_by, rejected_at, rejected_by, canceled_at, canceled_by) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)")
        }
        it("should build accept") {
          val q = UserRequestRepoSql.GroupInviteQueries.accept(req.id, user.id, now)
          check(q, s"UPDATE $table SET accepted_at=?, accepted_by=? WHERE ur.id=? AND ur.kind=? AND ur.accepted_at IS NULL AND ur.rejected_at IS NULL AND ur.canceled_at IS NULL")
        }
        it("should build reject") {
          val q = UserRequestRepoSql.GroupInviteQueries.reject(req.id, user.id, now)
          check(q, s"UPDATE $table SET rejected_at=?, rejected_by=? WHERE ur.id=? AND ur.kind=? AND ur.accepted_at IS NULL AND ur.rejected_at IS NULL AND ur.canceled_at IS NULL")
        }
        it("should build cancel") {
          val q = UserRequestRepoSql.GroupInviteQueries.cancel(req.id, user.id, now)
          check(q, s"UPDATE $table SET canceled_at=?, canceled_by=? WHERE ur.id=? AND ur.kind=? AND ur.accepted_at IS NULL AND ur.rejected_at IS NULL AND ur.canceled_at IS NULL")
        }
        it("should build selectOne") {
          val q = UserRequestRepoSql.GroupInviteQueries.selectOne(req.id)
          q.fr.update.sql shouldBe s"SELECT $groupInviteFields FROM $table WHERE ur.id=? $orderBy"
          // check(q) // ignored because missing "NOT NULL" on 'group_id', 'email' and 'created_by'... due to sealed trait
        }
        it("should build selectAllPending") {
          val q = UserRequestRepoSql.GroupInviteQueries.selectAllPending(group.id)
          q.fr.update.sql shouldBe s"SELECT $groupInviteFields FROM $table WHERE ur.kind=? AND ur.group_id=? AND ur.accepted_at IS NULL AND ur.rejected_at IS NULL AND ur.canceled_at IS NULL $orderBy"
          // check(q) // ignored because missing "NOT NULL" on 'group_id', 'email' and 'created_by'... due to sealed trait
        }
      }
      describe("TalkInviteQueries") {
        val req = TalkInvite(UserRequest.Id.generate(), talk.id, user.email, now, user.id, None, None, None)
        it("should build insert") {
          val q = UserRequestRepoSql.TalkInviteQueries.insert(req)
          check(q, s"INSERT INTO ${table.replaceAll(" ur", "")} (id, kind, talk_id, email, created_at, created_by, accepted_at, accepted_by, rejected_at, rejected_by, canceled_at, canceled_by) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)")
        }
        it("should build accept") {
          val q = UserRequestRepoSql.TalkInviteQueries.accept(req.id, user.id, now)
          check(q, s"UPDATE $table SET accepted_at=?, accepted_by=? WHERE ur.id=? AND ur.kind=? AND ur.accepted_at IS NULL AND ur.rejected_at IS NULL AND ur.canceled_at IS NULL")
        }
        it("should build reject") {
          val q = UserRequestRepoSql.TalkInviteQueries.reject(req.id, user.id, now)
          check(q, s"UPDATE $table SET rejected_at=?, rejected_by=? WHERE ur.id=? AND ur.kind=? AND ur.accepted_at IS NULL AND ur.rejected_at IS NULL AND ur.canceled_at IS NULL")
        }
        it("should build cancel") {
          val q = UserRequestRepoSql.TalkInviteQueries.cancel(req.id, user.id, now)
          check(q, s"UPDATE $table SET canceled_at=?, canceled_by=? WHERE ur.id=? AND ur.kind=? AND ur.accepted_at IS NULL AND ur.rejected_at IS NULL AND ur.canceled_at IS NULL")
        }
        it("should build selectOne") {
          val q = UserRequestRepoSql.TalkInviteQueries.selectOne(req.id)
          q.fr.update.sql shouldBe s"SELECT $talkInviteFields FROM $table WHERE ur.id=? $orderBy"
          // check(q) // ignored because missing "NOT NULL" on 'talk_id', 'email' and 'created_by'... due to sealed trait
        }
        it("should build selectAllPending") {
          val q = UserRequestRepoSql.TalkInviteQueries.selectAllPending(talk.id)
          q.fr.update.sql shouldBe s"SELECT $talkInviteFields FROM $table WHERE ur.kind=? AND ur.talk_id=? AND ur.accepted_at IS NULL AND ur.rejected_at IS NULL AND ur.canceled_at IS NULL $orderBy"
          // check(q) // ignored because missing "NOT NULL" on 'talk_id', 'email' and 'created_by'... due to sealed trait
        }
      }
      describe("ProposalInviteQueries") {
        val req = ProposalInvite(UserRequest.Id.generate(), proposal.id, user.email, now, user.id, None, None, None)
        it("should build insert") {
          val q = UserRequestRepoSql.ProposalInviteQueries.insert(req)
          check(q, s"INSERT INTO ${table.replaceAll(" ur", "")} (id, kind, proposal_id, email, created_at, created_by, accepted_at, accepted_by, rejected_at, rejected_by, canceled_at, canceled_by) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)")
        }
        it("should build accept") {
          val q = UserRequestRepoSql.ProposalInviteQueries.accept(req.id, user.id, now)
          check(q, s"UPDATE $table SET accepted_at=?, accepted_by=? WHERE ur.id=? AND ur.kind=? AND ur.accepted_at IS NULL AND ur.rejected_at IS NULL AND ur.canceled_at IS NULL")
        }
        it("should build reject") {
          val q = UserRequestRepoSql.ProposalInviteQueries.reject(req.id, user.id, now)
          check(q, s"UPDATE $table SET rejected_at=?, rejected_by=? WHERE ur.id=? AND ur.kind=? AND ur.accepted_at IS NULL AND ur.rejected_at IS NULL AND ur.canceled_at IS NULL")
        }
        it("should build cancel") {
          val q = UserRequestRepoSql.ProposalInviteQueries.cancel(req.id, user.id, now)
          check(q, s"UPDATE $table SET canceled_at=?, canceled_by=? WHERE ur.id=? AND ur.kind=? AND ur.accepted_at IS NULL AND ur.rejected_at IS NULL AND ur.canceled_at IS NULL")
        }
        it("should build selectOne") {
          val q = UserRequestRepoSql.ProposalInviteQueries.selectOne(req.id)
          q.fr.update.sql shouldBe s"SELECT $proposalInviteFields FROM $table WHERE ur.id=? $orderBy"
          // check(q) // ignored because missing "NOT NULL" on 'proposal_id', 'email' and 'created_by'... due to sealed trait
        }
        it("should build selectAllPending") {
          val q = UserRequestRepoSql.ProposalInviteQueries.selectAllPending(proposal.id)
          q.fr.update.sql shouldBe s"SELECT $proposalInviteFields FROM $table WHERE ur.kind=? AND ur.proposal_id=? AND ur.accepted_at IS NULL AND ur.rejected_at IS NULL AND ur.canceled_at IS NULL $orderBy"
          // check(q) // ignored because missing "NOT NULL" on 'proposal_id', 'email' and 'created_by'... due to sealed trait
        }
      }
    }
  }
}

object UserRequestRepoSqlSpec {
  val table = "user_requests ur"
  val fields: String = mapFields("id, kind, group_id, cfp_id, event_id, talk_id, proposal_id, email, payload, deadline, created_at, created_by, accepted_at, accepted_by, rejected_at, rejected_by, canceled_at, canceled_by", "ur." + _)
  val orderBy = "ORDER BY ur.created_at IS NULL, ur.created_at DESC"

  val accountValidationFields: String = mapFields("id, email, deadline, created_at, created_by, accepted_at", "ur." + _)
  val resetPasswordFields: String = mapFields("id, email, deadline, created_at, accepted_at", "ur." + _)
  val userAskToJoinAGroupFields: String = mapFields("id, group_id, created_at, created_by, accepted_at, accepted_by, rejected_at, rejected_by, canceled_at, canceled_by", "ur." + _)
  val groupInviteFields: String = mapFields("id, group_id, email, created_at, created_by, accepted_at, accepted_by, rejected_at, rejected_by, canceled_at, canceled_by", "ur." + _)
  val talkInviteFields: String = mapFields("id, talk_id, email, created_at, created_by, accepted_at, accepted_by, rejected_at, rejected_by, canceled_at, canceled_by", "ur." + _)
  val proposalInviteFields: String = mapFields("id, proposal_id, email, created_at, created_by, accepted_at, accepted_by, rejected_at, rejected_by, canceled_at, canceled_by", "ur." + _)
}
