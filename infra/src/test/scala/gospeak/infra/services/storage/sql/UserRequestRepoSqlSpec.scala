package gospeak.infra.services.storage.sql

import cats.data.NonEmptyList
import gospeak.core.domain.UserRequest
import gospeak.core.domain.UserRequest._
import gospeak.infra.services.storage.sql.UserRequestRepoSql._
import gospeak.infra.services.storage.sql.UserRequestRepoSqlSpec._
import gospeak.infra.services.storage.sql.testingutils.RepoSpec
import gospeak.infra.services.storage.sql.testingutils.RepoSpec.mapFields

class UserRequestRepoSqlSpec extends RepoSpec {
  private val accountValidation = AccountValidationRequest(UserRequest.Id.generate(), user.email, now, now, user.id, None)
  private val passwordReset = PasswordResetRequest(UserRequest.Id.generate(), user.email, now, now, None)
  private val userAskToJoinAGroup = UserAskToJoinAGroupRequest(UserRequest.Id.generate(), group.id, now, now, user.id, None, None, None)
  private val groupInvite = GroupInvite(UserRequest.Id.generate(), group.id, user.email, now, now, user.id, None, None, None)
  private val talkInvite = TalkInvite(UserRequest.Id.generate(), talk.id, user.email, now, now, user.id, None, None, None)
  private val proposalInvite = ProposalInvite(UserRequest.Id.generate(), proposal.id, user.email, now, now, user.id, None, None, None)
  private val externalProposalInvite = ExternalProposalInvite(UserRequest.Id.generate(), externalProposal.id, user.email, now, now, user.id, None, None, None)

  describe("UserRequestRepoSql") {
    it("should validate email") {
      val (user, ctx) = createUser().unsafeRunSync()
      userRepo.find(user.email).unsafeRunSync().flatMap(_.emailValidated) shouldBe None
      val req = userRequestRepo.createAccountValidationRequest(user.email, user.id, now).unsafeRunSync()
      userRequestRepo.findPendingAccountValidationRequest(user.id, now).unsafeRunSync() shouldBe Some(req)

      userRequestRepo.validateAccount(req.id, user.email, now).unsafeRunSync()
      userRequestRepo.findPendingAccountValidationRequest(user.id, now).unsafeRunSync() shouldBe None
      userRepo.find(user.email).unsafeRunSync().flatMap(_.emailValidated) shouldBe Some(now)
    }
    it("should reset password") {
      val (user, ctx) = createUser().unsafeRunSync()
      val req = userRequestRepo.createPasswordResetRequest(user.email, now).unsafeRunSync()
      userRequestRepo.findPendingPasswordResetRequest(req.id)(ctx.userAwareCtx).unsafeRunSync() shouldBe Some(req)
      userRepo.findCredentials(credentials.login).unsafeRunSync() shouldBe Some(credentials)

      val creds = credentials.copy(pass = credentials2.pass)
      userRequestRepo.resetPassword(req, creds, user).unsafeRunSync()
      userRequestRepo.findPendingPasswordResetRequest(req.id)(ctx.userAwareCtx).unsafeRunSync() shouldBe None
      userRepo.findCredentials(credentials.login).unsafeRunSync() shouldBe Some(creds)
    }
    it("should ask to join a group") {
      val (user, group, ctx) = createOrga().unsafeRunSync()
      val (user2, ctx2) = createUser(credentials2, userData2).unsafeRunSync()
      val req = userRequestRepo.createUserAskToJoinAGroup(group.id)(ctx2).unsafeRunSync()
      groupRepo.find(group.id).unsafeRunSync().map(_.owners) shouldBe Some(NonEmptyList.of(user.id))

      userRequestRepo.acceptUserToJoinAGroup(req)(ctx).unsafeRunSync()
      groupRepo.find(group.id).unsafeRunSync().map(_.owners) shouldBe Some(NonEmptyList.of(user.id, user2.id))
    }
    it("should invite user to a group") {
      val (user, group, ctx) = createOrga().unsafeRunSync()
      val (user2, ctx2) = createUser(credentials2, userData2).unsafeRunSync()
      val req = userRequestRepo.invite(user2.email)(ctx).unsafeRunSync()
      groupRepo.find(group.id).unsafeRunSync().map(_.owners) shouldBe Some(NonEmptyList.of(user.id))

      userRequestRepo.accept(req)(ctx2).unsafeRunSync()
      groupRepo.find(group.id).unsafeRunSync().map(_.owners) shouldBe Some(NonEmptyList.of(user.id, user2.id))
    }
    it("should invite user to a talk") {
      val (user, ctx) = createUser().unsafeRunSync()
      val (user2, ctx2) = createUser(credentials2, userData2).unsafeRunSync()
      val talk = talkRepo.create(talkData1)(ctx).unsafeRunSync()
      val req = userRequestRepo.invite(talk.id, user2.email)(ctx).unsafeRunSync()
      talkRepo.find(talk.id).unsafeRunSync().map(_.speakers) shouldBe Some(NonEmptyList.of(user.id))

      userRequestRepo.accept(req)(ctx2).unsafeRunSync()
      talkRepo.find(talk.id).unsafeRunSync().map(_.speakers) shouldBe Some(NonEmptyList.of(user.id, user2.id))
    }
    it("should invite user to a proposal") {
      val (user, group, cfp, partner, contact, venue, event, talk, proposal, ctx) = createProposal().unsafeRunSync()
      val (user2, ctx2) = createUser(credentials2, userData2).unsafeRunSync()
      val req = userRequestRepo.invite(proposal.id, user2.email, ctx.user.id, ctx.now).unsafeRunSync()
      proposalRepo.find(proposal.id).unsafeRunSync().map(_.speakers) shouldBe Some(NonEmptyList.of(user.id))

      userRequestRepo.accept(req)(ctx2).unsafeRunSync()
      proposalRepo.find(proposal.id).unsafeRunSync().map(_.speakers) shouldBe Some(NonEmptyList.of(user.id, user2.id))
    }
    it("should invite user to an external proposal") {
      val (user, ctx) = createUser().unsafeRunSync()
      val (user2, ctx2) = createUser(credentials2, userData2).unsafeRunSync()
      val talk = talkRepo.create(talkData1)(ctx).unsafeRunSync()
      val externalEvent = externalEventRepo.create(externalEventData1)(ctx).unsafeRunSync()
      val externalProposal = externalProposalRepo.create(talk.id, externalEvent.id, externalProposalData1, talk.speakers)(ctx).unsafeRunSync()
      val req = userRequestRepo.invite(externalProposal.id, user2.email)(ctx).unsafeRunSync()
      externalProposalRepo.find(externalProposal.id).unsafeRunSync().map(_.speakers) shouldBe Some(NonEmptyList.of(user.id))

      userRequestRepo.accept(req)(ctx2).unsafeRunSync()
      externalProposalRepo.find(externalProposal.id).unsafeRunSync().map(_.speakers) shouldBe Some(NonEmptyList.of(user.id, user2.id))
    }
    it("should check queries") {
      check(selectOne(userRequest.id), s"SELECT $fields FROM $table WHERE ur.id=? $orderBy")
      check(selectOnePending(group.id, userRequest.id, now), s"SELECT $fields FROM $table WHERE ur.id=? AND ur.group_id=? AND $isPending AND $notExpired $orderBy")
      check(selectAllPending(group.id, now), s"SELECT $fields FROM $table WHERE ur.group_id=? AND $isPending AND $notExpired $orderBy")

      check(AccountValidation.insert(accountValidation), s"INSERT INTO ${table.replaceAll(" ur", "")} (id, kind, email, deadline, created_at, created_by, accepted_at) VALUES (?, ?, ?, ?, ?, ?, ?)")
      check(AccountValidation.accept(accountValidation.id, now), s"UPDATE $table SET accepted_at=? WHERE ur.kind=? AND ur.id=? AND $isPending AND $notExpired")
      unsafeCheck(AccountValidation.selectOne(accountValidation.id), s"SELECT $accountValidationFields FROM $table WHERE ur.kind=? AND ur.id=? $orderBy")
      unsafeCheck(AccountValidation.selectPending(user.id, now), s"SELECT $accountValidationFields FROM $table WHERE ur.kind=? AND ur.created_by=? AND $isPending AND $notExpired $orderBy")

      check(PasswordReset.insert(passwordReset), s"INSERT INTO ${table.replaceAll(" ur", "")} (id, kind, email, deadline, created_at, accepted_at) VALUES (?, ?, ?, ?, ?, ?)")
      check(PasswordReset.accept(passwordReset.id, now), s"UPDATE $table SET accepted_at=? WHERE ur.kind=? AND ur.id=? AND $isPending AND $notExpired")
      unsafeCheck(PasswordReset.selectPending(passwordReset.id, now), s"SELECT $resetPasswordFields FROM $table WHERE ur.kind=? AND ur.id=? AND $isPending AND $notExpired $orderBy")
      unsafeCheck(PasswordReset.selectPending(user.email, now), s"SELECT $resetPasswordFields FROM $table WHERE ur.kind=? AND ur.email=? AND $isPending AND $notExpired $orderBy")

      check(UserAskToJoinAGroup.insert(userAskToJoinAGroup), s"INSERT INTO ${table.replaceAll(" ur", "")} (id, kind, group_id, deadline, created_at, created_by, accepted_at, accepted_by, rejected_at, rejected_by, canceled_at, canceled_by) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)")
      check(UserAskToJoinAGroup.accept(userAskToJoinAGroup.id, user.id, now), s"UPDATE $table SET accepted_at=?, accepted_by=? WHERE ur.kind=? AND ur.id=? AND $isPending AND $notExpired")
      check(UserAskToJoinAGroup.reject(userAskToJoinAGroup.id, user.id, now), s"UPDATE $table SET rejected_at=?, rejected_by=? WHERE ur.kind=? AND ur.id=? AND $isPending AND $notExpired")
      check(UserAskToJoinAGroup.cancel(userAskToJoinAGroup.id, user.id, now), s"UPDATE $table SET canceled_at=?, canceled_by=? WHERE ur.kind=? AND ur.id=? AND $isPending AND $notExpired")
      unsafeCheck(UserAskToJoinAGroup.selectOnePending(group.id, userRequest.id), s"SELECT $userAskToJoinAGroupFields FROM $table WHERE ur.kind=? AND ur.id=? AND $isPending AND ur.group_id=? $orderBy")
      unsafeCheck(UserAskToJoinAGroup.selectAllPending(user.id), s"SELECT $userAskToJoinAGroupFields FROM $table WHERE ur.kind=? AND ur.created_by=? AND $isPending $orderBy")

      check(GroupInviteQueries.insert(groupInvite), s"INSERT INTO ${table.replaceAll(" ur", "")} (id, kind, group_id, email, deadline, created_at, created_by, accepted_at, accepted_by, rejected_at, rejected_by, canceled_at, canceled_by) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)")
      check(GroupInviteQueries.accept(groupInvite.id, user.id, now), s"UPDATE $table SET accepted_at=?, accepted_by=? WHERE ur.kind=? AND ur.id=? AND $isPending AND $notExpired")
      check(GroupInviteQueries.reject(groupInvite.id, user.id, now), s"UPDATE $table SET rejected_at=?, rejected_by=? WHERE ur.kind=? AND ur.id=? AND $isPending AND $notExpired")
      check(GroupInviteQueries.cancel(groupInvite.id, user.id, now), s"UPDATE $table SET canceled_at=?, canceled_by=? WHERE ur.kind=? AND ur.id=? AND $isPending AND $notExpired")
      unsafeCheck(GroupInviteQueries.selectOne(groupInvite.id), s"SELECT $groupInviteFields FROM $table WHERE ur.id=? $orderBy")
      unsafeCheck(GroupInviteQueries.selectAllPending(group.id), s"SELECT $groupInviteFields FROM $table WHERE ur.kind=? AND $isPending AND ur.group_id=? $orderBy")

      check(TalkInviteQueries.insert(talkInvite), s"INSERT INTO ${table.replaceAll(" ur", "")} (id, kind, talk_id, email, deadline, created_at, created_by, accepted_at, accepted_by, rejected_at, rejected_by, canceled_at, canceled_by) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)")
      check(TalkInviteQueries.accept(talkInvite.id, user.id, now), s"UPDATE $table SET accepted_at=?, accepted_by=? WHERE ur.kind=? AND ur.id=? AND $isPending AND $notExpired")
      check(TalkInviteQueries.reject(talkInvite.id, user.id, now), s"UPDATE $table SET rejected_at=?, rejected_by=? WHERE ur.kind=? AND ur.id=? AND $isPending AND $notExpired")
      check(TalkInviteQueries.cancel(talkInvite.id, user.id, now), s"UPDATE $table SET canceled_at=?, canceled_by=? WHERE ur.kind=? AND ur.id=? AND $isPending AND $notExpired")
      unsafeCheck(TalkInviteQueries.selectOne(talkInvite.id), s"SELECT $talkInviteFields FROM $table WHERE ur.id=? $orderBy")
      unsafeCheck(TalkInviteQueries.selectAllPending(talk.id), s"SELECT $talkInviteFields FROM $table WHERE ur.kind=? AND ur.talk_id=? AND $isPending $orderBy")

      check(ProposalInviteQueries.insert(proposalInvite), s"INSERT INTO ${table.replaceAll(" ur", "")} (id, kind, proposal_id, email, deadline, created_at, created_by, accepted_at, accepted_by, rejected_at, rejected_by, canceled_at, canceled_by) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)")
      check(ProposalInviteQueries.accept(proposalInvite.id, user.id, now), s"UPDATE $table SET accepted_at=?, accepted_by=? WHERE ur.kind=? AND ur.id=? AND $isPending AND $notExpired")
      check(ProposalInviteQueries.reject(proposalInvite.id, user.id, now), s"UPDATE $table SET rejected_at=?, rejected_by=? WHERE ur.kind=? AND ur.id=? AND $isPending AND $notExpired")
      check(ProposalInviteQueries.cancel(proposalInvite.id, user.id, now), s"UPDATE $table SET canceled_at=?, canceled_by=? WHERE ur.kind=? AND ur.id=? AND $isPending AND $notExpired")
      unsafeCheck(ProposalInviteQueries.selectOne(proposalInvite.id), s"SELECT $proposalInviteFields FROM $table WHERE ur.id=? $orderBy")
      unsafeCheck(ProposalInviteQueries.selectAllPending(proposal.id), s"SELECT $proposalInviteFields FROM $table WHERE ur.kind=? AND ur.proposal_id=? AND $isPending $orderBy")

      check(ExternalProposalInviteQueries.insert(externalProposalInvite), s"INSERT INTO ${table.replaceAll(" ur", "")} (id, kind, external_proposal_id, email, deadline, created_at, created_by, accepted_at, accepted_by, rejected_at, rejected_by, canceled_at, canceled_by) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)")
      check(ExternalProposalInviteQueries.accept(externalProposalInvite.id, user.id, now), s"UPDATE $table SET accepted_at=?, accepted_by=? WHERE ur.kind=? AND ur.id=? AND $isPending AND $notExpired")
      check(ExternalProposalInviteQueries.reject(externalProposalInvite.id, user.id, now), s"UPDATE $table SET rejected_at=?, rejected_by=? WHERE ur.kind=? AND ur.id=? AND $isPending AND $notExpired")
      check(ExternalProposalInviteQueries.cancel(externalProposalInvite.id, user.id, now), s"UPDATE $table SET canceled_at=?, canceled_by=? WHERE ur.kind=? AND ur.id=? AND $isPending AND $notExpired")
      unsafeCheck(ExternalProposalInviteQueries.selectOne(externalProposalInvite.id), s"SELECT $externalProposalInviteFields FROM $table WHERE ur.id=? $orderBy")
      unsafeCheck(ExternalProposalInviteQueries.selectAllPending(externalProposal.id), s"SELECT $externalProposalInviteFields FROM $table WHERE ur.kind=? AND ur.external_proposal_id=? AND $isPending $orderBy")
    }
  }
}

object UserRequestRepoSqlSpec {
  val table = "user_requests ur"
  val fields: String = mapFields("id, kind, group_id, cfp_id, event_id, talk_id, proposal_id, external_event_id, external_cfp_id, external_proposal_id, email, payload, deadline, created_at, created_by, accepted_at, accepted_by, rejected_at, rejected_by, canceled_at, canceled_by", "ur." + _)
  val isPending = "ur.accepted_at IS NULL AND ur.rejected_at IS NULL AND ur.canceled_at IS NULL"
  val notExpired = "ur.deadline > ?"
  val orderBy = "ORDER BY ur.created_at IS NULL, ur.created_at DESC"

  val accountValidationFields: String = mapFields("id, email, deadline, created_at, created_by, accepted_at", "ur." + _)
  val resetPasswordFields: String = mapFields("id, email, deadline, created_at, accepted_at", "ur." + _)
  val userAskToJoinAGroupFields: String = mapFields("id, group_id, deadline, created_at, created_by, accepted_at, accepted_by, rejected_at, rejected_by, canceled_at, canceled_by", "ur." + _)
  val groupInviteFields: String = mapFields("id, group_id, email, deadline, created_at, created_by, accepted_at, accepted_by, rejected_at, rejected_by, canceled_at, canceled_by", "ur." + _)
  val talkInviteFields: String = mapFields("id, talk_id, email, deadline, created_at, created_by, accepted_at, accepted_by, rejected_at, rejected_by, canceled_at, canceled_by", "ur." + _)
  val proposalInviteFields: String = mapFields("id, proposal_id, email, deadline, created_at, created_by, accepted_at, accepted_by, rejected_at, rejected_by, canceled_at, canceled_by", "ur." + _)
  val externalProposalInviteFields: String = mapFields("id, external_proposal_id, email, deadline, created_at, created_by, accepted_at, accepted_by, rejected_at, rejected_by, canceled_at, canceled_by", "ur." + _)
}
