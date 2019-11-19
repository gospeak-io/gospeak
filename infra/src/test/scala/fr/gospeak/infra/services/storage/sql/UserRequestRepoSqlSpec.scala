package fr.gospeak.infra.services.storage.sql

import fr.gospeak.core.domain.UserRequest
import fr.gospeak.core.domain.UserRequest._
import fr.gospeak.infra.services.storage.sql.UserRequestRepoSqlSpec._
import fr.gospeak.infra.services.storage.sql.testingutils.RepoSpec
import fr.gospeak.infra.services.storage.sql.testingutils.RepoSpec.mapFields

class UserRequestRepoSqlSpec extends RepoSpec {
  describe("UserRequestRepoSql") {
    describe("Requests") {
      it("should create and retrieve a ProposalCreation request") {
        val u = userRepo.create(user.data, now).unsafeRunSync()
        val g = groupRepo.create(group.data, u.id, now).unsafeRunSync()
        val c = cfpRepo.create(g.id, cfp.data, u.id, now).unsafeRunSync()
        val e = eventRepo.create(g.id, event.data.copy(venue = None), u.id, now).unsafeRunSync()
        val req = userRequestRepo.createProposal(c.id, Some(e.id), u.email, ProposalCreation.Payload(u.slug, u.firstName, u.lastName, talk.slug, talk.title, talk.duration, talk.description, talk.tags), u.id, now).unsafeRunSync()
        userRequestRepo.find(req.id).unsafeRunSync() shouldBe Some(req)
        userRequestRepo.listPendingProposalCreations(e.id).unsafeRunSync() shouldBe Seq(req)
      }
    }
    describe("Queries") {
      it("should build accept") {
        val q = UserRequestRepoSql.accept(userRequest.id, "kind", user.id, now)
        check(q, s"UPDATE $table SET accepted=?, accepted_by=? WHERE r.id=? AND r.kind=? AND r.accepted IS NULL AND r.rejected IS NULL AND r.canceled IS NULL")
      }
      it("should build reject") {
        val q = UserRequestRepoSql.reject(userRequest.id, "kind", user.id, now)
        check(q, s"UPDATE $table SET rejected=?, rejected_by=? WHERE r.id=? AND r.kind=? AND r.accepted IS NULL AND r.rejected IS NULL AND r.canceled IS NULL")
      }
      it("should build cancel") {
        val q = UserRequestRepoSql.cancel(userRequest.id, "kind", user.id, now)
        check(q, s"UPDATE $table SET canceled=?, canceled_by=? WHERE r.id=? AND r.kind=? AND r.accepted IS NULL AND r.rejected IS NULL AND r.canceled IS NULL")
      }
      it("should build selectOne") {
        val q = UserRequestRepoSql.selectOne(userRequest.id, UserRequestRepoSql.UserAskToJoinAGroup.selectFields)
        q.fr.update.sql shouldBe s"SELECT $userAskToJoinAGroupFields FROM $table WHERE r.id=? $orderBy"
        // check(q) // ignored because missing "NOT NULL" on 'proposal_id', 'email' and 'created_by'... due to sealed trait
      }
      it("should build selectOne generic") {
        val q = UserRequestRepoSql.selectOne(userRequest.id)
        check(q, s"SELECT $fields FROM $table WHERE r.id=? $orderBy")
      }
      it("should build selectOnePending for group") {
        val q = UserRequestRepoSql.selectOnePending(group.id, userRequest.id, now)
        check(q, s"SELECT $fields FROM $table WHERE r.id=? AND r.group_id=? AND r.accepted IS NULL AND r.rejected IS NULL AND (r.deadline IS NULL OR r.deadline > ?) $orderBy")
      }
      it("should build selectPage for user") {
        val q = UserRequestRepoSql.selectPage(user.id, params)
        check(q, s"SELECT $fields FROM $table WHERE r.created_by=? $orderBy LIMIT 20 OFFSET 0")
      }
      it("should build selectAllPending for group") {
        val q = UserRequestRepoSql.selectAllPending(group.id, now)
        check(q, s"SELECT $fields FROM $table WHERE r.group_id=? AND r.accepted IS NULL AND r.rejected IS NULL AND (r.deadline IS NULL OR r.deadline > ?) $orderBy")
      }
      describe("AccountValidation") {
        val req = AccountValidationRequest(UserRequest.Id.generate(), user.email, now, now, user.id, None)
        it("should build insert") {
          val q = UserRequestRepoSql.AccountValidation.insert(req)
          check(q, s"INSERT INTO ${table.replaceAll(" r", "")} (id, kind, email, deadline, created, created_by, accepted) VALUES (?, ?, ?, ?, ?, ?, ?)")
        }
        it("should build accept") {
          val q = UserRequestRepoSql.AccountValidation.accept(req.id, now)
          check(q, s"UPDATE $table SET accepted=? WHERE r.id=? AND r.kind=? AND r.deadline > ? AND r.accepted IS NULL")
        }
        it("should build selectOne with id") {
          val q = UserRequestRepoSql.AccountValidation.selectOne(req.id)
          q.fr.update.sql shouldBe s"SELECT $accountValidationFields FROM $table WHERE r.id=? AND r.kind=? $orderBy"
          // check(q) // ignored because of missing "NOT NULL" due to sealed trait
        }
        it("should build selectPending with user") {
          val q = UserRequestRepoSql.AccountValidation.selectPending(user.id, now)
          q.fr.update.sql shouldBe s"SELECT $accountValidationFields FROM $table WHERE r.created_by=? AND r.kind=? AND r.deadline > ? AND r.accepted IS NULL $orderBy"
          // check(q) // ignored because of missing "NOT NULL" due to sealed trait
        }
      }
      describe("ResetPassword") {
        val req = PasswordResetRequest(UserRequest.Id.generate(), user.email, now, now, None)
        it("should build insert") {
          val q = UserRequestRepoSql.PasswordReset.insert(req)
          check(q, s"INSERT INTO ${table.replaceAll(" r", "")} (id, kind, email, deadline, created, accepted) VALUES (?, ?, ?, ?, ?, ?)")
        }
        it("should build accept") {
          val q = UserRequestRepoSql.PasswordReset.accept(req.id, now)
          check(q, s"UPDATE $table SET accepted=? WHERE r.id=? AND r.kind=? AND r.deadline > ? AND r.accepted IS NULL")
        }
        it("should build selectPending with id") {
          val q = UserRequestRepoSql.PasswordReset.selectPending(req.id, now)
          q.fr.update.sql shouldBe s"SELECT $resetPasswordFields FROM $table WHERE r.id=? AND r.kind=? AND r.deadline > ? AND r.accepted IS NULL $orderBy"
          // check(q) // ignored because of missing "NOT NULL" due to sealed trait
        }
        it("should build selectPending with email") {
          val q = UserRequestRepoSql.PasswordReset.selectPending(user.email, now)
          q.fr.update.sql shouldBe s"SELECT $resetPasswordFields FROM $table WHERE r.email=? AND r.kind=? AND r.deadline > ? AND r.accepted IS NULL $orderBy"
          // check(q) // ignored because of missing "NOT NULL" due to sealed trait
        }
      }
      describe("UserAskToJoinAGroup") {
        val req = UserAskToJoinAGroupRequest(UserRequest.Id.generate(), group.id, now, user.id, None, None, None)
        it("should build insert") {
          val q = UserRequestRepoSql.UserAskToJoinAGroup.insert(req)
          check(q, s"INSERT INTO ${table.replaceAll(" r", "")} (id, kind, group_id, created, created_by, accepted, accepted_by, rejected, rejected_by, canceled, canceled_by) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)")
        }
        it("should build selectOnePending") {
          val q = UserRequestRepoSql.UserAskToJoinAGroup.selectOnePending(group.id, userRequest.id)
          q.fr.update.sql shouldBe s"SELECT $userAskToJoinAGroupFields FROM $table WHERE r.id=? AND r.kind=? AND r.group_id=? AND r.accepted IS NULL AND r.rejected IS NULL AND r.canceled IS NULL $orderBy"
          // check(q) // ignored because missing "NOT NULL" on 'group_id' and 'created_by'... due to sealed trait
        }
        it("should build selectAllPending") {
          val q = UserRequestRepoSql.UserAskToJoinAGroup.selectAllPending(user.id)
          q.fr.update.sql shouldBe s"SELECT $userAskToJoinAGroupFields FROM $table WHERE r.kind=? AND r.created_by=? AND r.accepted IS NULL AND r.rejected IS NULL AND r.canceled IS NULL $orderBy"
          // check(q) // ignored because missing "NOT NULL" on 'group_id' and 'created_by'... due to sealed trait
        }
      }
      describe("GroupInviteQueries") {
        val req = GroupInvite(UserRequest.Id.generate(), group.id, user.email, now, user.id, None, None, None)
        it("should build insert") {
          val q = UserRequestRepoSql.GroupInviteQueries.insert(req)
          check(q, s"INSERT INTO ${table.replaceAll(" r", "")} (id, kind, group_id, email, created, created_by, accepted, accepted_by, rejected, rejected_by, canceled, canceled_by) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)")
        }
        it("should build selectAllPending") {
          val q = UserRequestRepoSql.GroupInviteQueries.selectAllPending(group.id)
          q.fr.update.sql shouldBe s"SELECT $groupInviteFields FROM $table WHERE r.kind=? AND r.group_id=? AND r.accepted IS NULL AND r.rejected IS NULL AND r.canceled IS NULL $orderBy"
          // check(q) // ignored because missing "NOT NULL" on 'group_id', 'email' and 'created_by'... due to sealed trait
        }
      }
      describe("TalkInviteQueries") {
        val req = TalkInvite(UserRequest.Id.generate(), talk.id, user.email, now, user.id, None, None, None)
        it("should build insert") {
          val q = UserRequestRepoSql.TalkInviteQueries.insert(req)
          check(q, s"INSERT INTO ${table.replaceAll(" r", "")} (id, kind, talk_id, email, created, created_by, accepted, accepted_by, rejected, rejected_by, canceled, canceled_by) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)")
        }
        it("should build selectAllPending") {
          val q = UserRequestRepoSql.TalkInviteQueries.selectAllPending(talk.id)
          q.fr.update.sql shouldBe s"SELECT $talkInviteFields FROM $table WHERE r.kind=? AND r.talk_id=? AND r.accepted IS NULL AND r.rejected IS NULL AND r.canceled IS NULL $orderBy"
          // check(q) // ignored because missing "NOT NULL" on 'talk_id', 'email' and 'created_by'... due to sealed trait
        }
      }
      describe("ProposalInviteQueries") {
        val req = ProposalInvite(UserRequest.Id.generate(), proposal.id, user.email, now, user.id, None, None, None)
        it("should build insert") {
          val q = UserRequestRepoSql.ProposalInviteQueries.insert(req)
          check(q, s"INSERT INTO ${table.replaceAll(" r", "")} (id, kind, proposal_id, email, created, created_by, accepted, accepted_by, rejected, rejected_by, canceled, canceled_by) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)")
        }
        it("should build selectAllPending") {
          val q = UserRequestRepoSql.ProposalInviteQueries.selectAllPending(proposal.id)
          q.fr.update.sql shouldBe s"SELECT $proposalInviteFields FROM $table WHERE r.kind=? AND r.proposal_id=? AND r.accepted IS NULL AND r.rejected IS NULL AND r.canceled IS NULL $orderBy"
          // check(q) // ignored because missing "NOT NULL" on 'proposal_id', 'email' and 'created_by'... due to sealed trait
        }
      }
      describe("ProposalCreationQueries") {
        val req = ProposalCreation(UserRequest.Id.generate(), cfp.id, Some(event.id), user.email, ProposalCreation.Payload(user.slug, user.firstName, user.lastName, talk.slug, talk.title, talk.duration, talk.description, talk.tags), now, user.id, None, None, None)
        it("should build insert") {
          val q = UserRequestRepoSql.ProposalCreationQueries.insert(req)
          check(q, s"INSERT INTO ${table.replaceAll(" r", "")} (id, kind, cfp_id, event_id, email, payload, created, created_by, accepted, accepted_by, rejected, rejected_by, canceled, canceled_by) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)")
        }
        it("should build selectAllPending for cfp") {
          val q = UserRequestRepoSql.ProposalCreationQueries.selectAllPending(cfp.id)
          q.fr.update.sql shouldBe s"SELECT $proposalCreationFields FROM $table WHERE r.kind=? AND r.cfp_id=? AND r.accepted IS NULL AND r.rejected IS NULL AND r.canceled IS NULL $orderBy"
          // check(q) // ignored because missing "NOT NULL" on 'proposal_id', 'email' and 'created_by'... due to sealed trait
        }
        it("should build selectAllPending for event") {
          val q = UserRequestRepoSql.ProposalCreationQueries.selectAllPending(event.id)
          q.fr.update.sql shouldBe s"SELECT $proposalCreationFields FROM $table WHERE r.kind=? AND r.event_id=? AND r.accepted IS NULL AND r.rejected IS NULL AND r.canceled IS NULL $orderBy"
          // check(q) // ignored because missing "NOT NULL" on 'proposal_id', 'email' and 'created_by'... due to sealed trait
        }
      }
    }
  }
}

object UserRequestRepoSqlSpec {
  val table = "requests r"
  val fields: String = mapFields("id, kind, group_id, cfp_id, event_id, talk_id, proposal_id, email, payload, deadline, created, created_by, accepted, accepted_by, rejected, rejected_by, canceled, canceled_by", "r." + _)
  val orderBy = "ORDER BY r.created IS NULL, r.created DESC"

  val accountValidationFields: String = mapFields("id, email, deadline, created, created_by, accepted", "r." + _)
  val resetPasswordFields: String = mapFields("id, email, deadline, created, accepted", "r." + _)
  val userAskToJoinAGroupFields: String = mapFields("id, group_id, created, created_by, accepted, accepted_by, rejected, rejected_by, canceled, canceled_by", "r." + _)
  val groupInviteFields: String = mapFields("id, group_id, email, created, created_by, accepted, accepted_by, rejected, rejected_by, canceled, canceled_by", "r." + _)
  val talkInviteFields: String = mapFields("id, talk_id, email, created, created_by, accepted, accepted_by, rejected, rejected_by, canceled, canceled_by", "r." + _)
  val proposalInviteFields: String = mapFields("id, proposal_id, email, created, created_by, accepted, accepted_by, rejected, rejected_by, canceled, canceled_by", "r." + _)
  val proposalCreationFields: String = mapFields("id, cfp_id, event_id, email, payload, created, created_by, accepted, accepted_by, rejected, rejected_by, canceled, canceled_by", "r." + _)
}
