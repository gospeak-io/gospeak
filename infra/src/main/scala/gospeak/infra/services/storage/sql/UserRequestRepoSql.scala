package gospeak.infra.services.storage.sql

import java.time.Instant

import cats.effect.IO
import doobie.syntax.string._
import fr.loicknuchel.safeql.{Cond, Query}
import gospeak.core.domain.UserRequest._
import gospeak.core.domain._
import gospeak.core.domain.utils.{OrgaCtx, UserAwareCtx, UserCtx}
import gospeak.core.services.storage.UserRequestRepo
import gospeak.infra.services.storage.sql.UserRequestRepoSql._
import gospeak.infra.services.storage.sql.database.Tables.USER_REQUESTS
import gospeak.infra.services.storage.sql.database.tables.USER_REQUESTS
import gospeak.infra.services.storage.sql.utils.DoobieMappings._
import gospeak.infra.services.storage.sql.utils.GenericRepo
import gospeak.libs.scala.Extensions._
import gospeak.libs.scala.domain.EmailAddress

class UserRequestRepoSql(protected[sql] val xa: doobie.Transactor[IO],
                         groupRepo: GroupRepoSql,
                         talkRepo: TalkRepoSql,
                         proposalRepo: ProposalRepoSql,
                         externalProposalRepo: ExternalProposalRepoSql) extends GenericRepo with UserRequestRepo {
  override def find(id: UserRequest.Id): IO[Option[UserRequest]] = selectOne(id).run(xa)

  override def listPendingGroupRequests(implicit ctx: OrgaCtx): IO[List[UserRequest]] = selectAllPending(ctx.group.id, ctx.now).run(xa)

  // override def findPending(group: Group.Id, req: UserRequest.Id, now: Instant): IO[Option[UserRequest]] = selectOnePending(group, req, now).run(xa)


  override def createAccountValidationRequest(email: EmailAddress, by: User.Id, now: Instant): IO[AccountValidationRequest] = {
    val req = AccountValidationRequest(UserRequest.Id.generate(), email, now.plus(Timeout.accountValidation), now, by, None)
    AccountValidation.insert(req).run(xa).map(_ => req)
  }

  override def validateAccount(id: UserRequest.Id, email: EmailAddress, now: Instant): IO[Unit] = for {
    _ <- AccountValidation.accept(id, now).run(xa)
    _ <- UserRepoSql.validateAccount(email, now).run(xa)
  } yield ()

  override def findAccountValidationRequest(id: UserRequest.Id): IO[Option[AccountValidationRequest]] = AccountValidation.selectOne(id).run(xa)

  override def findPendingAccountValidationRequest(id: User.Id, now: Instant): IO[Option[AccountValidationRequest]] = AccountValidation.selectPending(id, now).run(xa)


  override def createPasswordResetRequest(email: EmailAddress, now: Instant): IO[PasswordResetRequest] = {
    val req = PasswordResetRequest(UserRequest.Id.generate(), email, now.plus(Timeout.passwordReset), now, None)
    PasswordReset.insert(req).run(xa).map(_ => req)
  }

  override def resetPassword(req: PasswordResetRequest, credentials: User.Credentials, user: User)(implicit ctx: UserAwareCtx): IO[Unit] = for {
    _ <- PasswordReset.accept(req.id, ctx.now).run(xa)
    userOpt <- UserRepoSql.selectOne(credentials.login).run(xa)
    _ <- userOpt.map { _ =>
      UserRepoSql.updateCredentials(credentials.login)(credentials.pass).run(xa)
    }.getOrElse {
      UserRepoSql.insertLoginRef(User.LoginRef(credentials.login, user.id)).run(xa)
        .flatMap(_ => UserRepoSql.insertCredentials(credentials).run(xa))
    }
    _ <- UserRepoSql.validateAccount(req.email, ctx.now).run(xa)
  } yield ()

  override def findPendingPasswordResetRequest(id: UserRequest.Id)(implicit ctx: UserAwareCtx): IO[Option[PasswordResetRequest]] = PasswordReset.selectPending(id, ctx.now).run(xa)

  override def findPendingPasswordResetRequest(email: EmailAddress, now: Instant): IO[Option[PasswordResetRequest]] = PasswordReset.selectPending(email, now).run(xa)


  override def createUserAskToJoinAGroup(group: Group.Id)(implicit ctx: UserCtx): IO[UserAskToJoinAGroupRequest] = {
    val req = UserAskToJoinAGroupRequest(UserRequest.Id.generate(), group, ctx.now.plus(Timeout.default), ctx.now, ctx.user.id, None, None, None)
    UserAskToJoinAGroup.insert(req).run(xa).map(_ => req)
  }

  override def acceptUserToJoinAGroup(req: UserAskToJoinAGroupRequest)(implicit ctx: OrgaCtx): IO[Unit] = for {
    _ <- UserAskToJoinAGroup.accept(req.id, ctx.user.id, ctx.now).run(xa)
    _ <- groupRepo.addOwner(req.group, req.createdBy, ctx.user.id)
  } yield ()

  override def rejectUserToJoinAGroup(req: UserAskToJoinAGroupRequest)(implicit ctx: OrgaCtx): IO[Unit] = UserAskToJoinAGroup.reject(req.id, ctx.user.id, ctx.now).run(xa)

  override def findPendingUserToJoinAGroup(req: UserRequest.Id)(implicit ctx: OrgaCtx): IO[Option[UserAskToJoinAGroupRequest]] = UserAskToJoinAGroup.selectOnePending(ctx.group.id, req).run(xa)

  override def listPendingUserToJoinAGroupRequests(implicit ctx: UserCtx): IO[List[UserAskToJoinAGroupRequest]] = UserAskToJoinAGroup.selectAllPending(ctx.user.id).run(xa)


  override def invite(email: EmailAddress)(implicit ctx: OrgaCtx): IO[GroupInvite] = {
    val req = GroupInvite(UserRequest.Id.generate(), ctx.group.id, email, ctx.now.plus(Timeout.default), ctx.now, ctx.user.id, None, None, None)
    GroupInviteQueries.insert(req).run(xa).map(_ => req)
  }

  override def cancelGroupInvite(id: Id)(implicit ctx: OrgaCtx): IO[GroupInvite] =
    GroupInviteQueries.cancel(id, ctx.user.id, ctx.now).run(xa).flatMap(_ => GroupInviteQueries.selectOne(id).run(xa))

  override def accept(invite: UserRequest.GroupInvite)(implicit ctx: UserCtx): IO[Unit] = for {
    _ <- GroupInviteQueries.accept(invite.id, ctx.user.id, ctx.now).run(xa)
    _ <- groupRepo.addOwner(invite.group, ctx.user.id, invite.createdBy)
  } yield ()

  override def reject(invite: UserRequest.GroupInvite)(implicit ctx: UserCtx): IO[Unit] = GroupInviteQueries.reject(invite.id, ctx.user.id, ctx.now).run(xa)

  override def listPendingInvites(implicit ctx: OrgaCtx): IO[List[GroupInvite]] = GroupInviteQueries.selectAllPending(ctx.group.id).run(xa)


  override def invite(talk: Talk.Id, email: EmailAddress)(implicit ctx: UserCtx): IO[TalkInvite] = {
    val req = TalkInvite(UserRequest.Id.generate(), talk, email, ctx.now.plus(Timeout.default), ctx.now, ctx.user.id, None, None, None)
    TalkInviteQueries.insert(req).run(xa).map(_ => req)
  }

  override def cancelTalkInvite(id: UserRequest.Id)(implicit ctx: UserCtx): IO[TalkInvite] =
    TalkInviteQueries.cancel(id, ctx.user.id, ctx.now).run(xa).flatMap(_ => TalkInviteQueries.selectOne(id).run(xa))

  override def accept(invite: UserRequest.TalkInvite)(implicit ctx: UserCtx): IO[Unit] = for {
    _ <- TalkInviteQueries.accept(invite.id, ctx.user.id, ctx.now).run(xa)
    _ <- talkRepo.addSpeaker(invite.talk, invite.createdBy)
  } yield ()

  override def reject(invite: UserRequest.TalkInvite)(implicit ctx: UserCtx): IO[Unit] = TalkInviteQueries.reject(invite.id, ctx.user.id, ctx.now).run(xa)

  override def listPendingInvites(talk: Talk.Id): IO[List[TalkInvite]] = TalkInviteQueries.selectAllPending(talk).run(xa)


  override def invite(proposal: Proposal.Id, email: EmailAddress)(implicit ctx: UserCtx): IO[ProposalInvite] = invite(proposal, email, ctx.user.id, ctx.now)

  override def invite(proposal: Proposal.Id, email: EmailAddress)(implicit ctx: OrgaCtx): IO[ProposalInvite] = invite(proposal, email, ctx.user.id, ctx.now)

  private[sql] def invite(proposal: Proposal.Id, email: EmailAddress, user: User.Id, now: Instant): IO[ProposalInvite] = {
    val req = ProposalInvite(UserRequest.Id.generate(), proposal, email, now.plus(Timeout.default), now, user, None, None, None)
    ProposalInviteQueries.insert(req).run(xa).map(_ => req)
  }

  override def cancelProposalInvite(id: Id)(implicit ctx: UserCtx): IO[ProposalInvite] = cancelProposalInvite(id, ctx.user.id, ctx.now)

  override def cancelProposalInvite(id: Id)(implicit ctx: OrgaCtx): IO[ProposalInvite] = cancelProposalInvite(id, ctx.user.id, ctx.now)

  private def cancelProposalInvite(id: Id, user: User.Id, now: Instant): IO[ProposalInvite] =
    ProposalInviteQueries.cancel(id, user, now).run(xa).flatMap(_ => ProposalInviteQueries.selectOne(id).run(xa))

  override def accept(invite: UserRequest.ProposalInvite)(implicit ctx: UserCtx): IO[Unit] = for {
    _ <- ProposalInviteQueries.accept(invite.id, ctx.user.id, ctx.now).run(xa)
    _ <- proposalRepo.addSpeaker(invite.proposal)(ctx.user.id, invite.createdBy, ctx.now)
  } yield ()

  override def reject(invite: UserRequest.ProposalInvite)(implicit ctx: UserCtx): IO[Unit] = ProposalInviteQueries.reject(invite.id, ctx.user.id, ctx.now).run(xa)

  override def listPendingInvites(proposal: Proposal.Id): IO[List[ProposalInvite]] = ProposalInviteQueries.selectAllPending(proposal).run(xa)


  override def invite(proposal: ExternalProposal.Id, email: EmailAddress)(implicit ctx: UserCtx): IO[ExternalProposalInvite] = {
    val req = ExternalProposalInvite(UserRequest.Id.generate(), proposal, email, ctx.now.plus(Timeout.default), ctx.now, ctx.user.id, None, None, None)
    ExternalProposalInviteQueries.insert(req).run(xa).map(_ => req)
  }

  override def cancelExternalProposalInvite(id: UserRequest.Id)(implicit ctx: UserCtx): IO[ExternalProposalInvite] =
    ExternalProposalInviteQueries.cancel(id, ctx.user.id, ctx.now).run(xa).flatMap(_ => ExternalProposalInviteQueries.selectOne(id).run(xa))

  override def accept(invite: UserRequest.ExternalProposalInvite)(implicit ctx: UserCtx): IO[Unit] = for {
    _ <- ExternalProposalInviteQueries.accept(invite.id, ctx.user.id, ctx.now).run(xa)
    _ <- externalProposalRepo.addSpeaker(invite.externalProposal, ctx.user.id, invite.createdBy, ctx.now)
  } yield ()

  override def reject(invite: UserRequest.ExternalProposalInvite)(implicit ctx: UserCtx): IO[Unit] = ExternalProposalInviteQueries.reject(invite.id, ctx.user.id, ctx.now).run(xa)

  override def listPendingInvites(proposal: ExternalProposal.Id): IO[List[ExternalProposalInvite]] = ExternalProposalInviteQueries.selectAllPending(proposal).run(xa)
}

object UserRequestRepoSql {
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


  private[sql] def selectOne(id: UserRequest.Id): Query.Select.Optional[UserRequest] =
    USER_REQUESTS.select.where(_.ID is id).option[UserRequest]

  private[sql] def selectOnePending(group: Group.Id, req: UserRequest.Id, now: Instant): Query.Select.Optional[UserRequest] =
    USER_REQUESTS.select.where(ur => ur.ID.is(req) and ur.GROUP_ID.is(group) and IS_PENDING and NOT_EXPIRED(now)).option[UserRequest]

  private[sql] def selectAllPending(group: Group.Id, now: Instant): Query.Select.All[UserRequest] =
    USER_REQUESTS.select.where(_.GROUP_ID.is(group) and IS_PENDING and NOT_EXPIRED(now)).all[UserRequest]


  object AccountValidation {
    private val kind = "AccountValidation"
    private val FIELDS = List(USER_REQUESTS.ID, USER_REQUESTS.KIND, USER_REQUESTS.EMAIL, USER_REQUESTS.DEADLINE, USER_REQUESTS.CREATED_AT, USER_REQUESTS.CREATED_BY, USER_REQUESTS.ACCEPTED_AT)

    private[sql] def insert(e: AccountValidationRequest): Query.Insert[USER_REQUESTS] =
    // USER_REQUESTS.insert.fields(FIELDS).values(e.id, kind, e.email, e.deadline, e.createdAt, e.createdBy, e.acceptedAt)
      USER_REQUESTS.insert.fields(FIELDS).values(fr0"${e.id}, $kind, ${e.email}, ${e.deadline}, ${e.createdAt}, ${e.createdBy}, ${e.acceptedAt}")

    private[sql] def accept(req: UserRequest.Id, now: Instant): Query.Update[USER_REQUESTS] =
      USER_REQUESTS.update.set(_.ACCEPTED_AT, now).where(WHERE_PENDING(kind, req, now))

    private[sql] def selectOne(req: UserRequest.Id): Query.Select.Optional[AccountValidationRequest] =
      USER_REQUESTS.select.fields(FIELDS).dropFields(USER_REQUESTS.KIND).where(ur => ur.KIND.is(kind) and ur.ID.is(req)).option[AccountValidationRequest]

    private[sql] def selectPending(user: User.Id, now: Instant): Query.Select.Optional[AccountValidationRequest] =
      USER_REQUESTS.select.fields(FIELDS).dropFields(USER_REQUESTS.KIND).where(WHERE_PENDING(kind, user, now)).option[AccountValidationRequest]
  }

  object PasswordReset {
    private val kind = "PasswordReset"
    private val FIELDS = List(USER_REQUESTS.ID, USER_REQUESTS.KIND, USER_REQUESTS.EMAIL, USER_REQUESTS.DEADLINE, USER_REQUESTS.CREATED_AT, USER_REQUESTS.ACCEPTED_AT)

    private[sql] def insert(e: PasswordResetRequest): Query.Insert[USER_REQUESTS] =
    // USER_REQUESTS.insert.fields(FIELDS).values(e.id, kind, e.email, e.deadline, e.createdAt, e.acceptedAt)
      USER_REQUESTS.insert.fields(FIELDS).values(fr0"${e.id}, $kind, ${e.email}, ${e.deadline}, ${e.createdAt}, ${e.acceptedAt}")

    private[sql] def accept(req: UserRequest.Id, now: Instant): Query.Update[USER_REQUESTS] =
      USER_REQUESTS.update.set(_.ACCEPTED_AT, now).where(WHERE_PENDING(kind, req, now))

    private[sql] def selectPending(req: UserRequest.Id, now: Instant): Query.Select.Optional[PasswordResetRequest] =
      USER_REQUESTS.select.fields(FIELDS).dropFields(USER_REQUESTS.KIND).where(WHERE_PENDING(kind, req, now)).option[PasswordResetRequest]

    private[sql] def selectPending(email: EmailAddress, now: Instant): Query.Select.Optional[PasswordResetRequest] =
      USER_REQUESTS.select.fields(FIELDS).dropFields(USER_REQUESTS.KIND).where(WHERE_PENDING(kind, email, now)).option[PasswordResetRequest]
  }

  object UserAskToJoinAGroup {
    private val kind = "UserAskToJoinAGroup"
    private val FIELDS = List(USER_REQUESTS.ID, USER_REQUESTS.KIND, USER_REQUESTS.GROUP_ID, USER_REQUESTS.DEADLINE, USER_REQUESTS.CREATED_AT, USER_REQUESTS.CREATED_BY, USER_REQUESTS.ACCEPTED_AT, USER_REQUESTS.ACCEPTED_BY, USER_REQUESTS.REJECTED_AT, USER_REQUESTS.REJECTED_BY, USER_REQUESTS.CANCELED_AT, USER_REQUESTS.CANCELED_BY)
    private val SELECT_FIELDS = FIELDS.filter(_ != USER_REQUESTS.KIND)

    private[sql] def insert(e: UserAskToJoinAGroupRequest): Query.Insert[USER_REQUESTS] =
    // USER_REQUESTS.insert.fields(FIELDS).values(e.id, kind, e.group, e.deadline, e.createdAt, e.createdBy, e.accepted.map(_.date), e.accepted.map(_.by), e.rejected.map(_.date), e.rejected.map(_.by), e.canceled.map(_.date), e.canceled.map(_.by))
      USER_REQUESTS.insert.fields(FIELDS).values(fr0"${e.id}, $kind, ${e.group}, ${e.deadline}, ${e.createdAt}, ${e.createdBy}, ${e.accepted.map(_.date)}, ${e.accepted.map(_.by)}, ${e.rejected.map(_.date)}, ${e.rejected.map(_.by)}, ${e.canceled.map(_.date)}, ${e.canceled.map(_.by)}")

    private[sql] def accept(req: UserRequest.Id, by: User.Id, now: Instant): Query.Update[USER_REQUESTS] = UserRequestRepoSql.accept(req, by, now)(kind)

    private[sql] def reject(req: UserRequest.Id, by: User.Id, now: Instant): Query.Update[USER_REQUESTS] = UserRequestRepoSql.reject(req, by, now)(kind)

    private[sql] def cancel(req: UserRequest.Id, by: User.Id, now: Instant): Query.Update[USER_REQUESTS] = UserRequestRepoSql.cancel(req, by, now)(kind)

    private[sql] def selectOnePending(group: Group.Id, id: UserRequest.Id): Query.Select.Optional[UserAskToJoinAGroupRequest] =
      USER_REQUESTS.select.fields(SELECT_FIELDS).where(WHERE_PENDING(kind, id) and _.GROUP_ID.is(group)).option[UserAskToJoinAGroupRequest]

    private[sql] def selectAllPending(user: User.Id): Query.Select.All[UserAskToJoinAGroupRequest] =
      USER_REQUESTS.select.fields(SELECT_FIELDS).where(WHERE_PENDING(kind, user)).all[UserAskToJoinAGroupRequest]
  }

  object GroupInviteQueries {
    private val kind = "GroupInvite"
    private val FIELDS = List(USER_REQUESTS.ID, USER_REQUESTS.KIND, USER_REQUESTS.GROUP_ID, USER_REQUESTS.EMAIL, USER_REQUESTS.DEADLINE, USER_REQUESTS.CREATED_AT, USER_REQUESTS.CREATED_BY, USER_REQUESTS.ACCEPTED_AT, USER_REQUESTS.ACCEPTED_BY, USER_REQUESTS.REJECTED_AT, USER_REQUESTS.REJECTED_BY, USER_REQUESTS.CANCELED_AT, USER_REQUESTS.CANCELED_BY)
    private val SELECT_FIELDS = FIELDS.filter(_ != USER_REQUESTS.KIND)

    private[sql] def insert(e: GroupInvite): Query.Insert[USER_REQUESTS] =
    // USER_REQUESTS.insert.fields(FIELDS).values(e.id, kind, e.group, e.email, e.deadline, e.createdAt, e.createdBy, e.accepted.map(_.date), e.accepted.map(_.by), e.rejected.map(_.date), e.rejected.map(_.by), e.canceled.map(_.date), e.canceled.map(_.by))
      USER_REQUESTS.insert.fields(FIELDS).values(fr0"${e.id}, $kind, ${e.group}, ${e.email}, ${e.deadline}, ${e.createdAt}, ${e.createdBy}, ${e.accepted.map(_.date)}, ${e.accepted.map(_.by)}, ${e.rejected.map(_.date)}, ${e.rejected.map(_.by)}, ${e.canceled.map(_.date)}, ${e.canceled.map(_.by)}")

    private[sql] def accept(req: UserRequest.Id, by: User.Id, now: Instant): Query.Update[USER_REQUESTS] = UserRequestRepoSql.accept(req, by, now)(kind)

    private[sql] def reject(req: UserRequest.Id, by: User.Id, now: Instant): Query.Update[USER_REQUESTS] = UserRequestRepoSql.reject(req, by, now)(kind)

    private[sql] def cancel(req: UserRequest.Id, by: User.Id, now: Instant): Query.Update[USER_REQUESTS] = UserRequestRepoSql.cancel(req, by, now)(kind)

    private[sql] def selectOne(req: UserRequest.Id): Query.Select.One[GroupInvite] =
      USER_REQUESTS.select.fields(SELECT_FIELDS).where(_.ID is req).one[GroupInvite]

    private[sql] def selectAllPending(group: Group.Id): Query.Select.All[GroupInvite] =
      USER_REQUESTS.select.fields(SELECT_FIELDS).where(WHERE_PENDING(kind) and _.GROUP_ID.is(group)).all[GroupInvite]
  }

  object TalkInviteQueries {
    private val kind = "TalkInvite"
    private val FIELDS = List(USER_REQUESTS.ID, USER_REQUESTS.KIND, USER_REQUESTS.TALK_ID, USER_REQUESTS.EMAIL, USER_REQUESTS.DEADLINE, USER_REQUESTS.CREATED_AT, USER_REQUESTS.CREATED_BY, USER_REQUESTS.ACCEPTED_AT, USER_REQUESTS.ACCEPTED_BY, USER_REQUESTS.REJECTED_AT, USER_REQUESTS.REJECTED_BY, USER_REQUESTS.CANCELED_AT, USER_REQUESTS.CANCELED_BY)
    private val SELECT_FIELDS = FIELDS.filter(_ != USER_REQUESTS.KIND)

    private[sql] def insert(e: TalkInvite): Query.Insert[USER_REQUESTS] =
    // USER_REQUESTS.insert.fields(FIELDS).values(e.id, kind, e.talk, e.email, e.deadline, e.createdAt, e.createdBy, e.accepted.map(_.date), e.accepted.map(_.by), e.rejected.map(_.date), e.rejected.map(_.by), e.canceled.map(_.date), e.canceled.map(_.by))
      USER_REQUESTS.insert.fields(FIELDS).values(fr0"${e.id}, $kind, ${e.talk}, ${e.email}, ${e.deadline}, ${e.createdAt}, ${e.createdBy}, ${e.accepted.map(_.date)}, ${e.accepted.map(_.by)}, ${e.rejected.map(_.date)}, ${e.rejected.map(_.by)}, ${e.canceled.map(_.date)}, ${e.canceled.map(_.by)}")

    private[sql] def accept(req: UserRequest.Id, by: User.Id, now: Instant): Query.Update[USER_REQUESTS] = UserRequestRepoSql.accept(req, by, now)(kind)

    private[sql] def reject(req: UserRequest.Id, by: User.Id, now: Instant): Query.Update[USER_REQUESTS] = UserRequestRepoSql.reject(req, by, now)(kind)

    private[sql] def cancel(req: UserRequest.Id, by: User.Id, now: Instant): Query.Update[USER_REQUESTS] = UserRequestRepoSql.cancel(req, by, now)(kind)

    private[sql] def selectOne(req: UserRequest.Id): Query.Select.One[TalkInvite] =
      USER_REQUESTS.select.fields(SELECT_FIELDS).where(_.ID is req).one[TalkInvite]

    private[sql] def selectAllPending(talk: Talk.Id): Query.Select.All[TalkInvite] =
      USER_REQUESTS.select.fields(SELECT_FIELDS).where(WHERE_PENDING(kind, talk)).all[TalkInvite]
  }

  object ProposalInviteQueries {
    private val kind = "ProposalInvite"
    private val FIELDS = List(USER_REQUESTS.ID, USER_REQUESTS.KIND, USER_REQUESTS.PROPOSAL_ID, USER_REQUESTS.EMAIL, USER_REQUESTS.DEADLINE, USER_REQUESTS.CREATED_AT, USER_REQUESTS.CREATED_BY, USER_REQUESTS.ACCEPTED_AT, USER_REQUESTS.ACCEPTED_BY, USER_REQUESTS.REJECTED_AT, USER_REQUESTS.REJECTED_BY, USER_REQUESTS.CANCELED_AT, USER_REQUESTS.CANCELED_BY)
    private val SELECT_FIELDS = FIELDS.filter(_ != USER_REQUESTS.KIND)

    private[sql] def insert(e: ProposalInvite): Query.Insert[USER_REQUESTS] =
    // USER_REQUESTS.insert.fields(FIELDS).values(e.id, kind, e.proposal, e.email, e.deadline, e.createdAt, e.createdBy, e.accepted.map(_.date), e.accepted.map(_.by), e.rejected.map(_.date), e.rejected.map(_.by), e.canceled.map(_.date), e.canceled.map(_.by))
      USER_REQUESTS.insert.fields(FIELDS).values(fr0"${e.id}, $kind, ${e.proposal}, ${e.email}, ${e.deadline}, ${e.createdAt}, ${e.createdBy}, ${e.accepted.map(_.date)}, ${e.accepted.map(_.by)}, ${e.rejected.map(_.date)}, ${e.rejected.map(_.by)}, ${e.canceled.map(_.date)}, ${e.canceled.map(_.by)}")

    private[sql] def accept(req: UserRequest.Id, by: User.Id, now: Instant): Query.Update[USER_REQUESTS] = UserRequestRepoSql.accept(req, by, now)(kind)

    private[sql] def reject(req: UserRequest.Id, by: User.Id, now: Instant): Query.Update[USER_REQUESTS] = UserRequestRepoSql.reject(req, by, now)(kind)

    private[sql] def cancel(req: UserRequest.Id, by: User.Id, now: Instant): Query.Update[USER_REQUESTS] = UserRequestRepoSql.cancel(req, by, now)(kind)

    private[sql] def selectOne(req: UserRequest.Id): Query.Select.One[ProposalInvite] =
      USER_REQUESTS.select.fields(SELECT_FIELDS).where(_.ID is req).one[ProposalInvite]

    private[sql] def selectAllPending(proposal: Proposal.Id): Query.Select.All[ProposalInvite] =
      USER_REQUESTS.select.fields(SELECT_FIELDS).where(WHERE_PENDING(kind, proposal)).all[ProposalInvite]
  }

  object ExternalProposalInviteQueries {
    private val kind = "ExternalProposalInvite"
    private val FIELDS = List(USER_REQUESTS.ID, USER_REQUESTS.KIND, USER_REQUESTS.EXTERNAL_PROPOSAL_ID, USER_REQUESTS.EMAIL, USER_REQUESTS.DEADLINE, USER_REQUESTS.CREATED_AT, USER_REQUESTS.CREATED_BY, USER_REQUESTS.ACCEPTED_AT, USER_REQUESTS.ACCEPTED_BY, USER_REQUESTS.REJECTED_AT, USER_REQUESTS.REJECTED_BY, USER_REQUESTS.CANCELED_AT, USER_REQUESTS.CANCELED_BY)
    private val SELECT_FIELDS = FIELDS.filter(_ != USER_REQUESTS.KIND)

    private[sql] def insert(e: ExternalProposalInvite): Query.Insert[USER_REQUESTS] =
    // USER_REQUESTS.insert.fields(FIELDS).values(e.id, kind, e.externalProposal, e.email, e.deadline, e.createdAt, e.createdBy, e.accepted.map(_.date), e.accepted.map(_.by), e.rejected.map(_.date), e.rejected.map(_.by), e.canceled.map(_.date), e.canceled.map(_.by))
      USER_REQUESTS.insert.fields(FIELDS).values(fr0"${e.id}, $kind, ${e.externalProposal}, ${e.email}, ${e.deadline}, ${e.createdAt}, ${e.createdBy}, ${e.accepted.map(_.date)}, ${e.accepted.map(_.by)}, ${e.rejected.map(_.date)}, ${e.rejected.map(_.by)}, ${e.canceled.map(_.date)}, ${e.canceled.map(_.by)}")

    private[sql] def accept(req: UserRequest.Id, by: User.Id, now: Instant): Query.Update[USER_REQUESTS] = UserRequestRepoSql.accept(req, by, now)(kind)

    private[sql] def reject(req: UserRequest.Id, by: User.Id, now: Instant): Query.Update[USER_REQUESTS] = UserRequestRepoSql.reject(req, by, now)(kind)

    private[sql] def cancel(req: UserRequest.Id, by: User.Id, now: Instant): Query.Update[USER_REQUESTS] = UserRequestRepoSql.cancel(req, by, now)(kind)

    private[sql] def selectOne(req: UserRequest.Id): Query.Select.One[ExternalProposalInvite] =
      USER_REQUESTS.select.fields(SELECT_FIELDS).where(_.ID is req).one[ExternalProposalInvite]

    private[sql] def selectAllPending(externalProposal: ExternalProposal.Id): Query.Select.All[ExternalProposalInvite] =
      USER_REQUESTS.select.fields(SELECT_FIELDS).where(WHERE_PENDING(kind, externalProposal)).all[ExternalProposalInvite]
  }

  private def accept(req: UserRequest.Id, by: User.Id, now: Instant)(kind: String): Query.Update[USER_REQUESTS] =
    USER_REQUESTS.update.set(_.ACCEPTED_AT, now).set(_.ACCEPTED_BY, by).where(WHERE_PENDING(kind, req, now))

  private def reject(req: UserRequest.Id, by: User.Id, now: Instant)(kind: String): Query.Update[USER_REQUESTS] =
    USER_REQUESTS.update.set(_.REJECTED_AT, now).set(_.REJECTED_BY, by).where(WHERE_PENDING(kind, req, now))

  private def cancel(req: UserRequest.Id, by: User.Id, now: Instant)(kind: String): Query.Update[USER_REQUESTS] =
    USER_REQUESTS.update.set(_.CANCELED_AT, now).set(_.CANCELED_BY, by).where(WHERE_PENDING(kind, req, now))
}
