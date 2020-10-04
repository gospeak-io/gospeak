package gospeak.infra.services.storage.sql

import java.time.Instant

import cats.effect.IO
import doobie.syntax.string._
import doobie.util.fragment.Fragment
import gospeak.core.domain.UserRequest._
import gospeak.core.domain._
import gospeak.core.domain.utils.{OrgaCtx, UserAwareCtx, UserCtx}
import gospeak.core.services.storage.UserRequestRepo
import gospeak.infra.services.storage.sql.UserRequestRepoSql._
import gospeak.infra.services.storage.sql.database.Tables.USER_REQUESTS
import gospeak.infra.services.storage.sql.utils.DoobieMappings._
import gospeak.infra.services.storage.sql.utils.GenericRepo
import gospeak.libs.scala.Extensions._
import gospeak.libs.scala.domain.{Done, EmailAddress}
import gospeak.libs.sql.doobie.{Field, Query}
import gospeak.libs.sql.dsl.Cond

class UserRequestRepoSql(protected[sql] val xa: doobie.Transactor[IO],
                         groupRepo: GroupRepoSql,
                         talkRepo: TalkRepoSql,
                         proposalRepo: ProposalRepoSql,
                         externalProposalRepo: ExternalProposalRepoSql) extends GenericRepo with UserRequestRepo {
  override def find(id: UserRequest.Id): IO[Option[UserRequest]] = selectOne(id).runOption(xa)

  override def listPendingGroupRequests(implicit ctx: OrgaCtx): IO[List[UserRequest]] = selectAllPending(ctx.group.id, ctx.now).runList(xa)

  // override def findPending(group: Group.Id, req: UserRequest.Id, now: Instant): IO[Option[UserRequest]] = selectOnePending(group, req, now).runOption(xa)


  override def createAccountValidationRequest(email: EmailAddress, by: User.Id, now: Instant): IO[AccountValidationRequest] =
    AccountValidation.insert(AccountValidationRequest(UserRequest.Id.generate(), email, now.plus(Timeout.accountValidation), now, by, None)).run(xa)

  override def validateAccount(id: UserRequest.Id, email: EmailAddress, now: Instant): IO[Done] = for {
    _ <- AccountValidation.accept(id, now).run(xa)
    _ <- UserRepoSql.validateAccount(email, now).run(xa)
  } yield Done

  override def findAccountValidationRequest(id: UserRequest.Id): IO[Option[AccountValidationRequest]] = AccountValidation.selectOne(id).runOption(xa)

  override def findPendingAccountValidationRequest(id: User.Id, now: Instant): IO[Option[AccountValidationRequest]] = AccountValidation.selectPending(id, now).runOption(xa)


  override def createPasswordResetRequest(email: EmailAddress, now: Instant): IO[PasswordResetRequest] =
    PasswordReset.insert(PasswordResetRequest(UserRequest.Id.generate(), email, now.plus(Timeout.passwordReset), now, None)).run(xa)

  override def resetPassword(req: PasswordResetRequest, credentials: User.Credentials, user: User)(implicit ctx: UserAwareCtx): IO[Done] = for {
    _ <- PasswordReset.accept(req.id, ctx.now).run(xa)
    userOpt <- UserRepoSql.selectOne(credentials.login).runOption(xa)
    _ <- userOpt.map { _ =>
      UserRepoSql.updateCredentials(credentials.login)(credentials.pass).run(xa)
    }.getOrElse {
      UserRepoSql.insertLoginRef(User.LoginRef(credentials.login, user.id)).run(xa)
        .flatMap(_ => UserRepoSql.insertCredentials(credentials).run(xa)).map(_ => Done)
    }
    _ <- UserRepoSql.validateAccount(req.email, ctx.now).run(xa)
  } yield Done

  override def findPendingPasswordResetRequest(id: UserRequest.Id)(implicit ctx: UserAwareCtx): IO[Option[PasswordResetRequest]] = PasswordReset.selectPending(id, ctx.now).runOption(xa)

  override def findPendingPasswordResetRequest(email: EmailAddress, now: Instant): IO[Option[PasswordResetRequest]] = PasswordReset.selectPending(email, now).runOption(xa)


  override def createUserAskToJoinAGroup(group: Group.Id)(implicit ctx: UserCtx): IO[UserAskToJoinAGroupRequest] =
    UserAskToJoinAGroup.insert(UserAskToJoinAGroupRequest(UserRequest.Id.generate(), group, ctx.now.plus(Timeout.default), ctx.now, ctx.user.id, None, None, None)).run(xa)

  override def acceptUserToJoinAGroup(req: UserAskToJoinAGroupRequest)(implicit ctx: OrgaCtx): IO[Done] = for {
    _ <- UserAskToJoinAGroup.accept(req.id, ctx.user.id, ctx.now).run(xa)
    _ <- groupRepo.addOwner(req.group, req.createdBy, ctx.user.id)
  } yield Done

  override def rejectUserToJoinAGroup(req: UserAskToJoinAGroupRequest)(implicit ctx: OrgaCtx): IO[Done] = UserAskToJoinAGroup.reject(req.id, ctx.user.id, ctx.now).run(xa)

  override def findPendingUserToJoinAGroup(req: UserRequest.Id)(implicit ctx: OrgaCtx): IO[Option[UserAskToJoinAGroupRequest]] = UserAskToJoinAGroup.selectOnePending(ctx.group.id, req).runOption(xa)

  override def listPendingUserToJoinAGroupRequests(implicit ctx: UserCtx): IO[List[UserAskToJoinAGroupRequest]] = UserAskToJoinAGroup.selectAllPending(ctx.user.id).runList(xa)


  override def invite(email: EmailAddress)(implicit ctx: OrgaCtx): IO[GroupInvite] =
    GroupInviteQueries.insert(GroupInvite(UserRequest.Id.generate(), ctx.group.id, email, ctx.now.plus(Timeout.default), ctx.now, ctx.user.id, None, None, None)).run(xa)

  override def cancelGroupInvite(id: Id)(implicit ctx: OrgaCtx): IO[GroupInvite] =
    GroupInviteQueries.cancel(id, ctx.user.id, ctx.now).run(xa).flatMap(_ => GroupInviteQueries.selectOne(id).runUnique(xa))

  override def accept(invite: UserRequest.GroupInvite)(implicit ctx: UserCtx): IO[Done] = for {
    _ <- GroupInviteQueries.accept(invite.id, ctx.user.id, ctx.now).run(xa)
    _ <- groupRepo.addOwner(invite.group, ctx.user.id, invite.createdBy)
  } yield Done

  override def reject(invite: UserRequest.GroupInvite)(implicit ctx: UserCtx): IO[Done] = GroupInviteQueries.reject(invite.id, ctx.user.id, ctx.now).run(xa)

  override def listPendingInvites(implicit ctx: OrgaCtx): IO[List[GroupInvite]] = GroupInviteQueries.selectAllPending(ctx.group.id).runList(xa)


  override def invite(talk: Talk.Id, email: EmailAddress)(implicit ctx: UserCtx): IO[TalkInvite] =
    TalkInviteQueries.insert(TalkInvite(UserRequest.Id.generate(), talk, email, ctx.now.plus(Timeout.default), ctx.now, ctx.user.id, None, None, None)).run(xa)

  override def cancelTalkInvite(id: UserRequest.Id)(implicit ctx: UserCtx): IO[TalkInvite] =
    TalkInviteQueries.cancel(id, ctx.user.id, ctx.now).run(xa).flatMap(_ => TalkInviteQueries.selectOne(id).runUnique(xa))

  override def accept(invite: UserRequest.TalkInvite)(implicit ctx: UserCtx): IO[Done] = for {
    _ <- TalkInviteQueries.accept(invite.id, ctx.user.id, ctx.now).run(xa)
    _ <- talkRepo.addSpeaker(invite.talk, invite.createdBy)
  } yield Done

  override def reject(invite: UserRequest.TalkInvite)(implicit ctx: UserCtx): IO[Done] = TalkInviteQueries.reject(invite.id, ctx.user.id, ctx.now).run(xa)

  override def listPendingInvites(talk: Talk.Id): IO[List[TalkInvite]] = TalkInviteQueries.selectAllPending(talk).runList(xa)


  override def invite(proposal: Proposal.Id, email: EmailAddress)(implicit ctx: UserCtx): IO[ProposalInvite] = invite(proposal, email, ctx.user.id, ctx.now)

  override def invite(proposal: Proposal.Id, email: EmailAddress)(implicit ctx: OrgaCtx): IO[ProposalInvite] = invite(proposal, email, ctx.user.id, ctx.now)

  private[sql] def invite(proposal: Proposal.Id, email: EmailAddress, user: User.Id, now: Instant): IO[ProposalInvite] =
    ProposalInviteQueries.insert(ProposalInvite(UserRequest.Id.generate(), proposal, email, now.plus(Timeout.default), now, user, None, None, None)).run(xa)

  override def cancelProposalInvite(id: Id)(implicit ctx: UserCtx): IO[ProposalInvite] = cancelProposalInvite(id, ctx.user.id, ctx.now)

  override def cancelProposalInvite(id: Id)(implicit ctx: OrgaCtx): IO[ProposalInvite] = cancelProposalInvite(id, ctx.user.id, ctx.now)

  private def cancelProposalInvite(id: Id, user: User.Id, now: Instant): IO[ProposalInvite] =
    ProposalInviteQueries.cancel(id, user, now).run(xa).flatMap(_ => ProposalInviteQueries.selectOne(id).runUnique(xa))

  override def accept(invite: UserRequest.ProposalInvite)(implicit ctx: UserCtx): IO[Done] = for {
    _ <- ProposalInviteQueries.accept(invite.id, ctx.user.id, ctx.now).run(xa)
    _ <- proposalRepo.addSpeaker(invite.proposal)(ctx.user.id, invite.createdBy, ctx.now)
  } yield Done

  override def reject(invite: UserRequest.ProposalInvite)(implicit ctx: UserCtx): IO[Done] = ProposalInviteQueries.reject(invite.id, ctx.user.id, ctx.now).run(xa)

  override def listPendingInvites(proposal: Proposal.Id): IO[List[ProposalInvite]] = ProposalInviteQueries.selectAllPending(proposal).runList(xa)


  override def invite(proposal: ExternalProposal.Id, email: EmailAddress)(implicit ctx: UserCtx): IO[ExternalProposalInvite] =
    ExternalProposalInviteQueries.insert(ExternalProposalInvite(UserRequest.Id.generate(), proposal, email, ctx.now.plus(Timeout.default), ctx.now, ctx.user.id, None, None, None)).run(xa)

  override def cancelExternalProposalInvite(id: UserRequest.Id)(implicit ctx: UserCtx): IO[ExternalProposalInvite] =
    ExternalProposalInviteQueries.cancel(id, ctx.user.id, ctx.now).run(xa).flatMap(_ => ExternalProposalInviteQueries.selectOne(id).runUnique(xa))

  override def accept(invite: UserRequest.ExternalProposalInvite)(implicit ctx: UserCtx): IO[Done] = for {
    _ <- ExternalProposalInviteQueries.accept(invite.id, ctx.user.id, ctx.now).run(xa)
    _ <- externalProposalRepo.addSpeaker(invite.externalProposal, ctx.user.id, invite.createdBy, ctx.now)
  } yield Done

  override def reject(invite: UserRequest.ExternalProposalInvite)(implicit ctx: UserCtx): IO[Done] = ExternalProposalInviteQueries.reject(invite.id, ctx.user.id, ctx.now).run(xa)

  override def listPendingInvites(proposal: ExternalProposal.Id): IO[List[ExternalProposalInvite]] = ExternalProposalInviteQueries.selectAllPending(proposal).runList(xa)
}

object UserRequestRepoSql {
  private val _ = userRequestIdMeta // for intellij not remove DoobieMappings import
  private val table = Tables.userRequests
  private val andIsPending = fr0" AND ur.accepted_at IS NULL AND ur.rejected_at IS NULL AND ur.canceled_at IS NULL"

  private def andNotExpired(now: Instant): Fragment = fr0" AND ur.deadline > $now"

  private def wherePending(kind: String): Fragment = fr0"ur.kind=$kind" ++ andIsPending

  private def wherePending(kind: String, req: UserRequest.Id): Fragment = fr0"ur.kind=$kind AND ur.id=$req" ++ andIsPending

  private def wherePending(kind: String, user: User.Id): Fragment = fr0"ur.kind=$kind AND ur.created_by=$user" ++ andIsPending

  private def wherePending(kind: String, talk: Talk.Id): Fragment = fr0"ur.kind=$kind AND ur.talk_id=$talk" ++ andIsPending

  private def wherePending(kind: String, proposal: Proposal.Id): Fragment = fr0"ur.kind=$kind AND ur.proposal_id=$proposal" ++ andIsPending

  private def wherePending(kind: String, proposal: ExternalProposal.Id): Fragment = fr0"ur.kind=$kind AND ur.external_proposal_id=$proposal" ++ andIsPending

  private def wherePending(kind: String, req: UserRequest.Id, now: Instant): Fragment = fr0"ur.kind=$kind AND ur.id=$req" ++ andIsPending ++ andNotExpired(now)

  private def wherePending(kind: String, user: User.Id, now: Instant): Fragment = fr0"ur.kind=$kind AND ur.created_by=$user" ++ andIsPending ++ andNotExpired(now)

  private def wherePending(kind: String, email: EmailAddress, now: Instant): Fragment = fr0"ur.kind=$kind AND ur.email=$email" ++ andIsPending ++ andNotExpired(now)

  private val IS_PENDING: Cond = USER_REQUESTS.ACCEPTED_AT.isNull and USER_REQUESTS.REJECTED_AT.isNull and USER_REQUESTS.CANCELED_AT.isNull

  private def NOT_EXPIRED(now: Instant): Cond = USER_REQUESTS.DEADLINE gt now

  private def WHERE_PENDING(kind: String): Cond = USER_REQUESTS.KIND.is(kind) and IS_PENDING

  private def WHERE_PENDING(kind: String, req: UserRequest.Id): Cond = USER_REQUESTS.KIND.is(kind) and USER_REQUESTS.ID.is(req) and IS_PENDING

  private def WHERE_PENDING(kind: String, user: User.Id): Cond = USER_REQUESTS.KIND.is(kind) and USER_REQUESTS.CREATED_BY.is(user) and IS_PENDING

  private def WHERE_PENDING(kind: String, talk: Talk.Id): Cond = USER_REQUESTS.KIND.is(kind) and USER_REQUESTS.TALK_ID.is(talk) and IS_PENDING

  private def WHERE_PENDING(kind: String, proposal: Proposal.Id): Cond = USER_REQUESTS.KIND.is(kind) and USER_REQUESTS.PROPOSAL_ID.is(proposal) and IS_PENDING

  private def WHERE_PENDING(kind: String, proposal: ExternalProposal.Id): Cond = USER_REQUESTS.KIND.is(kind) and USER_REQUESTS.EXTERNAL_PROPOSAL_ID.is(proposal) and IS_PENDING

  private def WHERE_PENDING(kind: String, req: UserRequest.Id, now: Instant): Cond = USER_REQUESTS.KIND.is(kind) and USER_REQUESTS.ID.is(req) and IS_PENDING and NOT_EXPIRED(now)

  private def WHERE_PENDING(kind: String, user: User.Id, now: Instant): Cond = USER_REQUESTS.KIND.is(kind) and USER_REQUESTS.CREATED_BY.is(user) and IS_PENDING and NOT_EXPIRED(now)

  private def WHERE_PENDING(kind: String, email: EmailAddress, now: Instant): Cond = USER_REQUESTS.KIND.is(kind) and USER_REQUESTS.EMAIL.is(email) and IS_PENDING and NOT_EXPIRED(now)


  private[sql] def selectOne(id: UserRequest.Id): Query.Select[UserRequest] = {
    val q1 = table.select[UserRequest].where(fr0"ur.id=$id")
    val q2 = USER_REQUESTS.select.where(_.ID is id).option[UserRequest]
    GenericRepo.assertEqual(q1.fr, q2.fr)
    q1
  }

  private[sql] def selectOnePending(group: Group.Id, req: UserRequest.Id, now: Instant): Query.Select[UserRequest] = {
    val q1 = table.select[UserRequest].where(fr0"ur.id=$req AND ur.group_id=$group" ++ andIsPending ++ andNotExpired(now))
    val q2 = USER_REQUESTS.select.where(ur => ur.ID.is(req) and ur.GROUP_ID.is(group) and IS_PENDING and NOT_EXPIRED(now)).option[UserRequest]
    GenericRepo.assertEqual(q1.fr, q2.fr)
    q1
  }

  private[sql] def selectAllPending(group: Group.Id, now: Instant): Query.Select[UserRequest] = {
    val q1 = table.select[UserRequest].where(fr0"ur.group_id=$group" ++ andIsPending ++ andNotExpired(now))
    val q2 = USER_REQUESTS.select.where(_.GROUP_ID.is(group) and IS_PENDING and NOT_EXPIRED(now)).all[UserRequest]
    GenericRepo.assertEqual(q1.fr, q2.fr)
    q1
  }


  object AccountValidation {
    private val kind = "AccountValidation"
    private val fields = List("id", "kind", "email", "deadline", "created_at", "created_by", "accepted_at").map(n => Field(n, table.prefix))
    private val FIELDS = List(USER_REQUESTS.ID, USER_REQUESTS.KIND, USER_REQUESTS.EMAIL, USER_REQUESTS.DEADLINE, USER_REQUESTS.CREATED_AT, USER_REQUESTS.CREATED_BY, USER_REQUESTS.ACCEPTED_AT)

    private[sql] def insert(e: AccountValidationRequest): Query.Insert[AccountValidationRequest] = {
      val values = fr0"${e.id}, $kind, ${e.email}, ${e.deadline}, ${e.createdAt}, ${e.createdBy}, ${e.acceptedAt}"
      val q1 = table.insertPartial[AccountValidationRequest](fields, e, _ => values)
      val q2 = USER_REQUESTS.insert.fields(FIELDS).values(e.id, kind, e.email, e.deadline, e.createdAt, e.createdBy, e.acceptedAt)
      GenericRepo.assertEqual(q1.fr, q2.fr)
      q1
    }

    private[sql] def accept(req: UserRequest.Id, now: Instant): Query.Update = {
      val q1 = table.update(fr0"accepted_at=$now").where(wherePending(kind, req, now))
      val q2 = USER_REQUESTS.update.setOpt(_.ACCEPTED_AT, now).where(WHERE_PENDING(kind, req, now))
      GenericRepo.assertEqual(q1.fr, q2.fr)
      q1
    }

    private[sql] def selectOne(req: UserRequest.Id): Query.Select[AccountValidationRequest] = {
      val q1 = table.select[AccountValidationRequest].fields(fields.filter(_.name != "kind")).where(fr0"ur.kind=$kind AND ur.id=$req")
      val q2 = USER_REQUESTS.select.fields(FIELDS).dropFields(USER_REQUESTS.KIND).where(ur => ur.KIND.is(kind) and ur.ID.is(req)).option[AccountValidationRequest]
      GenericRepo.assertEqual(q1.fr, q2.fr)
      q1
    }

    private[sql] def selectPending(user: User.Id, now: Instant): Query.Select[AccountValidationRequest] = {
      val q1 = table.select[AccountValidationRequest].fields(fields.filter(_.name != "kind")).where(wherePending(kind, user, now))
      val q2 = USER_REQUESTS.select.fields(FIELDS).dropFields(USER_REQUESTS.KIND).where(WHERE_PENDING(kind, user, now)).option[AccountValidationRequest]
      GenericRepo.assertEqual(q1.fr, q2.fr)
      q1
    }
  }

  object PasswordReset {
    private val kind = "PasswordReset"
    private val fields = List("id", "kind", "email", "deadline", "created_at", "accepted_at").map(n => Field(n, table.prefix))
    private val FIELDS = List(USER_REQUESTS.ID, USER_REQUESTS.KIND, USER_REQUESTS.EMAIL, USER_REQUESTS.DEADLINE, USER_REQUESTS.CREATED_AT, USER_REQUESTS.ACCEPTED_AT)

    private[sql] def insert(e: PasswordResetRequest): Query.Insert[PasswordResetRequest] = {
      val values = fr0"${e.id}, $kind, ${e.email}, ${e.deadline}, ${e.createdAt}, ${e.acceptedAt}"
      val q1 = table.insertPartial[PasswordResetRequest](fields, e, _ => values)
      val q2 = USER_REQUESTS.insert.fields(FIELDS).values(e.id, kind, e.email, e.deadline, e.createdAt, e.acceptedAt)
      GenericRepo.assertEqual(q1.fr, q2.fr)
      q1
    }

    private[sql] def accept(req: UserRequest.Id, now: Instant): Query.Update = {
      val q1 = table.update(fr0"accepted_at=$now").where(wherePending(kind, req, now))
      val q2 = USER_REQUESTS.update.setOpt(_.ACCEPTED_AT, now).where(WHERE_PENDING(kind, req, now))
      GenericRepo.assertEqual(q1.fr, q2.fr)
      q1
    }

    private[sql] def selectPending(req: UserRequest.Id, now: Instant): Query.Select[PasswordResetRequest] = {
      val q1 = table.select[PasswordResetRequest].fields(fields.filter(_.name != "kind")).where(wherePending(kind, req, now))
      val q2 = USER_REQUESTS.select.fields(FIELDS).dropFields(USER_REQUESTS.KIND).where(WHERE_PENDING(kind, req, now)).option[PasswordResetRequest]
      GenericRepo.assertEqual(q1.fr, q2.fr)
      q1
    }

    private[sql] def selectPending(email: EmailAddress, now: Instant): Query.Select[PasswordResetRequest] = {
      val q1 = table.select[PasswordResetRequest].fields(fields.filter(_.name != "kind")).where(wherePending(kind, email, now))
      val q2 = USER_REQUESTS.select.fields(FIELDS).dropFields(USER_REQUESTS.KIND).where(WHERE_PENDING(kind, email, now)).option[PasswordResetRequest]
      GenericRepo.assertEqual(q1.fr, q2.fr)
      q1
    }
  }

  object UserAskToJoinAGroup {
    private val kind = "UserAskToJoinAGroup"
    private val fields = List("id", "kind", "group_id", "deadline", "created_at", "created_by", "accepted_at", "accepted_by", "rejected_at", "rejected_by", "canceled_at", "canceled_by").map(n => Field(n, table.prefix))
    private val selectFields = fields.filter(_.name != "kind")
    private val FIELDS = List(USER_REQUESTS.ID, USER_REQUESTS.KIND, USER_REQUESTS.GROUP_ID, USER_REQUESTS.DEADLINE, USER_REQUESTS.CREATED_AT, USER_REQUESTS.CREATED_BY, USER_REQUESTS.ACCEPTED_AT, USER_REQUESTS.ACCEPTED_BY, USER_REQUESTS.REJECTED_AT, USER_REQUESTS.REJECTED_BY, USER_REQUESTS.CANCELED_AT, USER_REQUESTS.CANCELED_BY)
    private val SELECT_FIELDS = FIELDS.filter(_ != USER_REQUESTS.KIND)

    private[sql] def insert(e: UserAskToJoinAGroupRequest): Query.Insert[UserAskToJoinAGroupRequest] = {
      val values = fr0"${e.id}, $kind, ${e.group}, ${e.deadline}, ${e.createdAt}, ${e.createdBy}, ${e.accepted.map(_.date)}, ${e.accepted.map(_.by)}, ${e.rejected.map(_.date)}, ${e.rejected.map(_.by)}, ${e.canceled.map(_.date)}, ${e.canceled.map(_.by)}"
      val q1 = table.insertPartial[UserAskToJoinAGroupRequest](fields, e, _ => values)
      val q2 = USER_REQUESTS.insert.fields(FIELDS).values(e.id, kind, e.group, e.deadline, e.createdAt, e.createdBy, e.accepted.map(_.date), e.accepted.map(_.by), e.rejected.map(_.date), e.rejected.map(_.by), e.canceled.map(_.date), e.canceled.map(_.by))
      GenericRepo.assertEqual(q1.fr, q2.fr)
      q1
    }

    private[sql] def accept(req: UserRequest.Id, by: User.Id, now: Instant): Query.Update = UserRequestRepoSql.accept(req, by, now)(kind)

    private[sql] def reject(req: UserRequest.Id, by: User.Id, now: Instant): Query.Update = UserRequestRepoSql.reject(req, by, now)(kind)

    private[sql] def cancel(req: UserRequest.Id, by: User.Id, now: Instant): Query.Update = UserRequestRepoSql.cancel(req, by, now)(kind)

    private[sql] def selectOnePending(group: Group.Id, id: UserRequest.Id): Query.Select[UserAskToJoinAGroupRequest] = {
      val q1 = table.select[UserAskToJoinAGroupRequest].fields(selectFields).where(wherePending(kind, id) ++ fr0" AND ur.group_id=$group")
      val q2 = USER_REQUESTS.select.fields(SELECT_FIELDS).where(WHERE_PENDING(kind, id) and _.GROUP_ID.is(group)).option[UserAskToJoinAGroupRequest]
      GenericRepo.assertEqual(q1.fr, q2.fr)
      q1
    }

    private[sql] def selectAllPending(user: User.Id): Query.Select[UserAskToJoinAGroupRequest] = {
      val q1 = table.select[UserAskToJoinAGroupRequest].fields(selectFields).where(wherePending(kind, user))
      val q2 = USER_REQUESTS.select.fields(SELECT_FIELDS).where(WHERE_PENDING(kind, user)).all[UserAskToJoinAGroupRequest]
      GenericRepo.assertEqual(q1.fr, q2.fr)
      q1
    }
  }

  object GroupInviteQueries {
    private val kind = "GroupInvite"
    private val fields = List("id", "kind", "group_id", "email", "deadline", "created_at", "created_by", "accepted_at", "accepted_by", "rejected_at", "rejected_by", "canceled_at", "canceled_by").map(n => Field(n, table.prefix))
    private val selectFields = fields.filter(_.name != "kind")
    private val FIELDS = List(USER_REQUESTS.ID, USER_REQUESTS.KIND, USER_REQUESTS.GROUP_ID, USER_REQUESTS.EMAIL, USER_REQUESTS.DEADLINE, USER_REQUESTS.CREATED_AT, USER_REQUESTS.CREATED_BY, USER_REQUESTS.ACCEPTED_AT, USER_REQUESTS.ACCEPTED_BY, USER_REQUESTS.REJECTED_AT, USER_REQUESTS.REJECTED_BY, USER_REQUESTS.CANCELED_AT, USER_REQUESTS.CANCELED_BY)
    private val SELECT_FIELDS = FIELDS.filter(_ != USER_REQUESTS.KIND)

    private[sql] def insert(e: GroupInvite): Query.Insert[GroupInvite] = {
      val values = fr0"${e.id}, $kind, ${e.group}, ${e.email}, ${e.deadline}, ${e.createdAt}, ${e.createdBy}, ${e.accepted.map(_.date)}, ${e.accepted.map(_.by)}, ${e.rejected.map(_.date)}, ${e.rejected.map(_.by)}, ${e.canceled.map(_.date)}, ${e.canceled.map(_.by)}"
      val q1 = table.insertPartial[GroupInvite](fields, e, _ => values)
      val q2 = USER_REQUESTS.insert.fields(FIELDS).values(e.id, kind, e.group, e.email, e.deadline, e.createdAt, e.createdBy, e.accepted.map(_.date), e.accepted.map(_.by), e.rejected.map(_.date), e.rejected.map(_.by), e.canceled.map(_.date), e.canceled.map(_.by))
      GenericRepo.assertEqual(q1.fr, q2.fr)
      q1
    }

    private[sql] def accept(req: UserRequest.Id, by: User.Id, now: Instant): Query.Update = UserRequestRepoSql.accept(req, by, now)(kind)

    private[sql] def reject(req: UserRequest.Id, by: User.Id, now: Instant): Query.Update = UserRequestRepoSql.reject(req, by, now)(kind)

    private[sql] def cancel(req: UserRequest.Id, by: User.Id, now: Instant): Query.Update = UserRequestRepoSql.cancel(req, by, now)(kind)

    private[sql] def selectOne(req: UserRequest.Id): Query.Select[GroupInvite] = {
      val q1 = table.select[GroupInvite].fields(selectFields).where(fr0"ur.id=$req")
      val q2 = USER_REQUESTS.select.fields(SELECT_FIELDS).where(_.ID is req).option[GroupInvite]
      GenericRepo.assertEqual(q1.fr, q2.fr)
      q1
    }

    private[sql] def selectAllPending(group: Group.Id): Query.Select[GroupInvite] = {
      val q1 = table.select[GroupInvite].fields(selectFields).where(wherePending(kind) ++ fr0" AND ur.group_id=$group")
      val q2 = USER_REQUESTS.select.fields(SELECT_FIELDS).where(WHERE_PENDING(kind) and _.GROUP_ID.is(group)).all[GroupInvite]
      GenericRepo.assertEqual(q1.fr, q2.fr)
      q1
    }
  }

  object TalkInviteQueries {
    private val kind = "TalkInvite"
    private val fields = List("id", "kind", "talk_id", "email", "deadline", "created_at", "created_by", "accepted_at", "accepted_by", "rejected_at", "rejected_by", "canceled_at", "canceled_by").map(n => Field(n, table.prefix))
    private val selectFields = fields.filter(_.name != "kind")
    private val FIELDS = List(USER_REQUESTS.ID, USER_REQUESTS.KIND, USER_REQUESTS.TALK_ID, USER_REQUESTS.EMAIL, USER_REQUESTS.DEADLINE, USER_REQUESTS.CREATED_AT, USER_REQUESTS.CREATED_BY, USER_REQUESTS.ACCEPTED_AT, USER_REQUESTS.ACCEPTED_BY, USER_REQUESTS.REJECTED_AT, USER_REQUESTS.REJECTED_BY, USER_REQUESTS.CANCELED_AT, USER_REQUESTS.CANCELED_BY)
    private val SELECT_FIELDS = FIELDS.filter(_ != USER_REQUESTS.KIND)

    private[sql] def insert(e: TalkInvite): Query.Insert[TalkInvite] = {
      val values = fr0"${e.id}, $kind, ${e.talk}, ${e.email}, ${e.deadline}, ${e.createdAt}, ${e.createdBy}, ${e.accepted.map(_.date)}, ${e.accepted.map(_.by)}, ${e.rejected.map(_.date)}, ${e.rejected.map(_.by)}, ${e.canceled.map(_.date)}, ${e.canceled.map(_.by)}"
      val q1 = table.insertPartial[TalkInvite](fields, e, _ => values)
      val q2 = USER_REQUESTS.insert.fields(FIELDS).values(e.id, kind, e.talk, e.email, e.deadline, e.createdAt, e.createdBy, e.accepted.map(_.date), e.accepted.map(_.by), e.rejected.map(_.date), e.rejected.map(_.by), e.canceled.map(_.date), e.canceled.map(_.by))
      GenericRepo.assertEqual(q1.fr, q2.fr)
      q1
    }

    private[sql] def accept(req: UserRequest.Id, by: User.Id, now: Instant): Query.Update = UserRequestRepoSql.accept(req, by, now)(kind)

    private[sql] def reject(req: UserRequest.Id, by: User.Id, now: Instant): Query.Update = UserRequestRepoSql.reject(req, by, now)(kind)

    private[sql] def cancel(req: UserRequest.Id, by: User.Id, now: Instant): Query.Update = UserRequestRepoSql.cancel(req, by, now)(kind)

    private[sql] def selectOne(req: UserRequest.Id): Query.Select[TalkInvite] = {
      val q1 = table.select[TalkInvite].fields(selectFields).where(fr0"ur.id=$req")
      val q2 = USER_REQUESTS.select.fields(SELECT_FIELDS).where(_.ID is req).one[TalkInvite]
      GenericRepo.assertEqual(q1.fr, q2.fr)
      q1
    }

    private[sql] def selectAllPending(talk: Talk.Id): Query.Select[TalkInvite] = {
      val q1 = table.select[TalkInvite].fields(selectFields).where(wherePending(kind, talk))
      val q2 = USER_REQUESTS.select.fields(SELECT_FIELDS).where(WHERE_PENDING(kind, talk)).all[TalkInvite]
      GenericRepo.assertEqual(q1.fr, q2.fr)
      q1
    }
  }

  object ProposalInviteQueries {
    private val kind = "ProposalInvite"
    private val fields = List("id", "kind", "proposal_id", "email", "deadline", "created_at", "created_by", "accepted_at", "accepted_by", "rejected_at", "rejected_by", "canceled_at", "canceled_by").map(n => Field(n, table.prefix))
    private val selectFields = fields.filter(_.name != "kind")
    private val FIELDS = List(USER_REQUESTS.ID, USER_REQUESTS.KIND, USER_REQUESTS.PROPOSAL_ID, USER_REQUESTS.EMAIL, USER_REQUESTS.DEADLINE, USER_REQUESTS.CREATED_AT, USER_REQUESTS.CREATED_BY, USER_REQUESTS.ACCEPTED_AT, USER_REQUESTS.ACCEPTED_BY, USER_REQUESTS.REJECTED_AT, USER_REQUESTS.REJECTED_BY, USER_REQUESTS.CANCELED_AT, USER_REQUESTS.CANCELED_BY)
    private val SELECT_FIELDS = FIELDS.filter(_ != USER_REQUESTS.KIND)

    private[sql] def insert(e: ProposalInvite): Query.Insert[ProposalInvite] = {
      val values = fr0"${e.id}, $kind, ${e.proposal}, ${e.email}, ${e.deadline}, ${e.createdAt}, ${e.createdBy}, ${e.accepted.map(_.date)}, ${e.accepted.map(_.by)}, ${e.rejected.map(_.date)}, ${e.rejected.map(_.by)}, ${e.canceled.map(_.date)}, ${e.canceled.map(_.by)}"
      val q1 = table.insertPartial[ProposalInvite](fields, e, _ => values)
      val q2 = USER_REQUESTS.insert.fields(FIELDS).values(e.id, kind, e.proposal, e.email, e.deadline, e.createdAt, e.createdBy, e.accepted.map(_.date), e.accepted.map(_.by), e.rejected.map(_.date), e.rejected.map(_.by), e.canceled.map(_.date), e.canceled.map(_.by))
      GenericRepo.assertEqual(q1.fr, q2.fr)
      q1
    }

    private[sql] def accept(req: UserRequest.Id, by: User.Id, now: Instant): Query.Update = UserRequestRepoSql.accept(req, by, now)(kind)

    private[sql] def reject(req: UserRequest.Id, by: User.Id, now: Instant): Query.Update = UserRequestRepoSql.reject(req, by, now)(kind)

    private[sql] def cancel(req: UserRequest.Id, by: User.Id, now: Instant): Query.Update = UserRequestRepoSql.cancel(req, by, now)(kind)

    private[sql] def selectOne(req: UserRequest.Id): Query.Select[ProposalInvite] = {
      val q1 = table.select[ProposalInvite].fields(selectFields).where(fr0"ur.id=$req")
      val q2 = USER_REQUESTS.select.fields(SELECT_FIELDS).where(_.ID is req).one[ProposalInvite]
      GenericRepo.assertEqual(q1.fr, q2.fr)
      q1
    }

    private[sql] def selectAllPending(proposal: Proposal.Id): Query.Select[ProposalInvite] = {
      val q1 = table.select[ProposalInvite].fields(selectFields).where(wherePending(kind, proposal))
      val q2 = USER_REQUESTS.select.fields(SELECT_FIELDS).where(WHERE_PENDING(kind, proposal)).all[ProposalInvite]
      GenericRepo.assertEqual(q1.fr, q2.fr)
      q1
    }
  }

  object ExternalProposalInviteQueries {
    private val kind = "ExternalProposalInvite"
    private val fields = List("id", "kind", "external_proposal_id", "email", "deadline", "created_at", "created_by", "accepted_at", "accepted_by", "rejected_at", "rejected_by", "canceled_at", "canceled_by").map(n => Field(n, table.prefix))
    private val selectFields = fields.filter(_.name != "kind")
    private val FIELDS = List(USER_REQUESTS.ID, USER_REQUESTS.KIND, USER_REQUESTS.EXTERNAL_PROPOSAL_ID, USER_REQUESTS.EMAIL, USER_REQUESTS.DEADLINE, USER_REQUESTS.CREATED_AT, USER_REQUESTS.CREATED_BY, USER_REQUESTS.ACCEPTED_AT, USER_REQUESTS.ACCEPTED_BY, USER_REQUESTS.REJECTED_AT, USER_REQUESTS.REJECTED_BY, USER_REQUESTS.CANCELED_AT, USER_REQUESTS.CANCELED_BY)
    private val SELECT_FIELDS = FIELDS.filter(_ != USER_REQUESTS.KIND)

    private[sql] def insert(e: ExternalProposalInvite): Query.Insert[ExternalProposalInvite] = {
      val values = fr0"${e.id}, $kind, ${e.externalProposal}, ${e.email}, ${e.deadline}, ${e.createdAt}, ${e.createdBy}, ${e.accepted.map(_.date)}, ${e.accepted.map(_.by)}, ${e.rejected.map(_.date)}, ${e.rejected.map(_.by)}, ${e.canceled.map(_.date)}, ${e.canceled.map(_.by)}"
      val q1 = table.insertPartial[ExternalProposalInvite](fields, e, _ => values)
      val q2 = USER_REQUESTS.insert.fields(FIELDS).values(e.id, kind, e.externalProposal, e.email, e.deadline, e.createdAt, e.createdBy, e.accepted.map(_.date), e.accepted.map(_.by), e.rejected.map(_.date), e.rejected.map(_.by), e.canceled.map(_.date), e.canceled.map(_.by))
      GenericRepo.assertEqual(q1.fr, q2.fr)
      q1
    }

    private[sql] def accept(req: UserRequest.Id, by: User.Id, now: Instant): Query.Update = UserRequestRepoSql.accept(req, by, now)(kind)

    private[sql] def reject(req: UserRequest.Id, by: User.Id, now: Instant): Query.Update = UserRequestRepoSql.reject(req, by, now)(kind)

    private[sql] def cancel(req: UserRequest.Id, by: User.Id, now: Instant): Query.Update = UserRequestRepoSql.cancel(req, by, now)(kind)

    private[sql] def selectOne(req: UserRequest.Id): Query.Select[ExternalProposalInvite] = {
      val q1 = table.select[ExternalProposalInvite].fields(selectFields).where(fr0"ur.id=$req")
      val q2 = USER_REQUESTS.select.fields(SELECT_FIELDS).where(_.ID is req).one[ExternalProposalInvite]
      GenericRepo.assertEqual(q1.fr, q2.fr)
      q1
    }

    private[sql] def selectAllPending(externalProposal: ExternalProposal.Id): Query.Select[ExternalProposalInvite] = {
      val q1 = table.select[ExternalProposalInvite].fields(selectFields).where(wherePending(kind, externalProposal))
      val q2 = USER_REQUESTS.select.fields(SELECT_FIELDS).where(WHERE_PENDING(kind, externalProposal)).all[ExternalProposalInvite]
      GenericRepo.assertEqual(q1.fr, q2.fr)
      q1
    }
  }

  private def accept(req: UserRequest.Id, by: User.Id, now: Instant)(kind: String): Query.Update = {
    val q1 = table.update(fr0"accepted_at=$now, accepted_by=$by").where(wherePending(kind, req, now))
    val q2 = USER_REQUESTS.update.setOpt(_.ACCEPTED_AT, now).setOpt(_.ACCEPTED_BY, by).where(WHERE_PENDING(kind, req, now))
    GenericRepo.assertEqual(q1.fr, q2.fr)
    q1
  }

  private def reject(req: UserRequest.Id, by: User.Id, now: Instant)(kind: String): Query.Update = {
    val q1 = table.update(fr0"rejected_at=$now, rejected_by=$by").where(wherePending(kind, req, now))
    val q2 = USER_REQUESTS.update.setOpt(_.REJECTED_AT, now).setOpt(_.REJECTED_BY, by).where(WHERE_PENDING(kind, req, now))
    GenericRepo.assertEqual(q1.fr, q2.fr)
    q1
  }

  private def cancel(req: UserRequest.Id, by: User.Id, now: Instant)(kind: String): Query.Update = {
    val q1 = table.update(fr0"canceled_at=$now, canceled_by=$by").where(wherePending(kind, req, now))
    val q2 = USER_REQUESTS.update.setOpt(_.CANCELED_AT, now).setOpt(_.CANCELED_BY, by).where(WHERE_PENDING(kind, req, now))
    GenericRepo.assertEqual(q1.fr, q2.fr)
    q1
  }
}
