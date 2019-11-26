package fr.gospeak.infra.services.storage.sql

import java.time.Instant

import cats.effect.IO
import doobie.implicits._
import doobie.util.fragment.Fragment
import fr.gospeak.core.domain.UserRequest._
import fr.gospeak.core.domain._
import fr.gospeak.core.domain.utils.{OrgaCtx, UserCtx}
import fr.gospeak.core.services.storage.UserRequestRepo
import fr.gospeak.infra.services.storage.sql.UserRequestRepoSql._
import fr.gospeak.infra.services.storage.sql.utils.DoobieUtils.Mappings._
import fr.gospeak.infra.services.storage.sql.utils.DoobieUtils.{Field, Insert, Select, SelectPage, Update}
import fr.gospeak.infra.services.storage.sql.utils.GenericRepo
import fr.gospeak.libs.scalautils.Extensions._
import fr.gospeak.libs.scalautils.domain.{Done, EmailAddress, Page}

class UserRequestRepoSql(groupRepo: GroupRepoSql,
                         talkRepo: TalkRepoSql,
                         proposalRepo: ProposalRepoSql,
                         protected[sql] val xa: doobie.Transactor[IO]) extends GenericRepo with UserRequestRepo {
  override def find(id: UserRequest.Id): IO[Option[UserRequest]] = selectOne(id).runOption(xa)

  override def list(user: User.Id, params: Page.Params): IO[Page[UserRequest]] = selectPage(user, params).run(xa)

  override def listPendingGroupRequests(implicit ctx: OrgaCtx): IO[Seq[UserRequest]] = selectAllPending(ctx.group.id, ctx.now).runList(xa)

  // override def findPending(group: Group.Id, req: UserRequest.Id, now: Instant): IO[Option[UserRequest]] = selectOnePending(group, req, now).runOption(xa)

  override def findPendingUserToJoinAGroup(req: UserRequest.Id)(implicit ctx: OrgaCtx): IO[Option[UserAskToJoinAGroupRequest]] =
    UserAskToJoinAGroup.selectOnePending(ctx.group.id, req).runOption(xa)

  override def createAccountValidationRequest(email: EmailAddress, user: User.Id, now: Instant): IO[AccountValidationRequest] =
    AccountValidation.insert(AccountValidationRequest(UserRequest.Id.generate(), email, now.plus(Timeout.accountValidation), now, user, None)).run(xa)

  override def validateAccount(id: UserRequest.Id, email: EmailAddress, now: Instant): IO[Done] = for {
    _ <- AccountValidation.accept(id, now).run(xa)
    _ <- UserRepoSql.validateAccount(email, now).run(xa)
  } yield Done

  override def findAccountValidationRequest(id: UserRequest.Id): IO[Option[AccountValidationRequest]] = AccountValidation.selectOne(id).runOption(xa)

  override def findPendingAccountValidationRequest(id: User.Id, now: Instant): IO[Option[AccountValidationRequest]] = AccountValidation.selectPending(id, now).runOption(xa)


  override def createPasswordResetRequest(email: EmailAddress, now: Instant): IO[PasswordResetRequest] =
    PasswordReset.insert(PasswordResetRequest(UserRequest.Id.generate(), email, now, now.plus(Timeout.passwordReset), None)).run(xa)

  override def resetPassword(passwordReset: PasswordResetRequest, credentials: User.Credentials, now: Instant): IO[Done] = for {
    _ <- PasswordReset.accept(passwordReset.id, now).run(xa)
    _ <- UserRepoSql.updateCredentials(credentials.login)(credentials.pass).run(xa)
    _ <- UserRepoSql.validateAccount(passwordReset.email, now).run(xa)
  } yield Done

  override def findPendingPasswordResetRequest(id: UserRequest.Id, now: Instant): IO[Option[PasswordResetRequest]] = PasswordReset.selectPending(id, now).runOption(xa)

  override def findPendingPasswordResetRequest(email: EmailAddress, now: Instant): IO[Option[PasswordResetRequest]] = PasswordReset.selectPending(email, now).runOption(xa)


  override def createUserAskToJoinAGroup(group: Group.Id)(implicit ctx: UserCtx): IO[UserAskToJoinAGroupRequest] =
    UserAskToJoinAGroup.insert(UserAskToJoinAGroupRequest(UserRequest.Id.generate(), group, ctx.now, ctx.user.id, None, None, None)).run(xa)

  override def acceptUserToJoinAGroup(req: UserAskToJoinAGroupRequest)(implicit ctx: OrgaCtx): IO[Done] = for {
    _ <- UserAskToJoinAGroup.accept(req.id, ctx.user.id, ctx.now).run(xa)
    _ <- groupRepo.addOwner(req.group, req.createdBy, ctx.user.id)
  } yield Done

  override def rejectUserToJoinAGroup(req: UserAskToJoinAGroupRequest)(implicit ctx: OrgaCtx): IO[Done] = UserAskToJoinAGroup.reject(req.id, ctx.user.id, ctx.now).run(xa)

  override def listPendingUserToJoinAGroupRequests(implicit ctx: UserCtx): IO[Seq[UserAskToJoinAGroupRequest]] = UserAskToJoinAGroup.selectAllPending(ctx.user.id).runList(xa)


  override def invite(email: EmailAddress)(implicit ctx: OrgaCtx): IO[GroupInvite] =
    GroupInviteQueries.insert(GroupInvite(UserRequest.Id.generate(), ctx.group.id, email, ctx.now, ctx.user.id, None, None, None)).run(xa)

  override def cancelGroupInvite(id: Id)(implicit ctx: OrgaCtx): IO[GroupInvite] =
    GroupInviteQueries.cancel(id, ctx.user.id, ctx.now).run(xa).flatMap(_ => GroupInviteQueries.selectOne(id).runUnique(xa))

  override def accept(invite: UserRequest.GroupInvite)(implicit ctx: UserCtx): IO[Done] = for {
    _ <- GroupInviteQueries.accept(invite.id, ctx.user.id, ctx.now).run(xa)
    _ <- groupRepo.addOwner(invite.group, ctx.user.id, invite.createdBy)
  } yield Done

  override def reject(invite: UserRequest.GroupInvite)(implicit ctx: UserCtx): IO[Done] = GroupInviteQueries.reject(invite.id, ctx.user.id, ctx.now).run(xa)

  override def listPendingInvites(implicit ctx: OrgaCtx): IO[Seq[GroupInvite]] = GroupInviteQueries.selectAllPending(ctx.group.id).runList(xa)


  override def invite(talk: Talk.Id, email: EmailAddress)(implicit ctx: UserCtx): IO[TalkInvite] =
    TalkInviteQueries.insert(TalkInvite(UserRequest.Id.generate(), talk, email, ctx.now, ctx.user.id, None, None, None)).run(xa)

  override def cancelTalkInvite(id: UserRequest.Id)(implicit ctx: UserCtx): IO[TalkInvite] =
    TalkInviteQueries.cancel(id, ctx.user.id, ctx.now).run(xa).flatMap(_ => TalkInviteQueries.selectOne(id).runUnique(xa))

  override def accept(invite: UserRequest.TalkInvite)(implicit ctx: UserCtx): IO[Done] = for {
    _ <- TalkInviteQueries.accept(invite.id, ctx.user.id, ctx.now).run(xa)
    _ <- talkRepo.addSpeaker(invite.talk, invite.createdBy)
  } yield Done

  override def reject(invite: UserRequest.TalkInvite)(implicit ctx: UserCtx): IO[Done] = TalkInviteQueries.reject(invite.id, ctx.user.id, ctx.now).run(xa)

  override def listPendingInvites(talk: Talk.Id): IO[Seq[TalkInvite]] = TalkInviteQueries.selectAllPending(talk).runList(xa)


  override def invite(proposal: Proposal.Id, email: EmailAddress)(implicit ctx: UserCtx): IO[ProposalInvite] =
    ProposalInviteQueries.insert(ProposalInvite(UserRequest.Id.generate(), proposal, email, ctx.now, ctx.user.id, None, None, None)).run(xa)

  override def invite(proposal: Proposal.Id, email: EmailAddress)(implicit ctx: OrgaCtx): IO[ProposalInvite] =
    ProposalInviteQueries.insert(ProposalInvite(UserRequest.Id.generate(), proposal, email, ctx.now, ctx.user.id, None, None, None)).run(xa)

  override def cancelProposalInvite(id: Id)(implicit ctx: UserCtx): IO[ProposalInvite] =
    ProposalInviteQueries.cancel(id, ctx.user.id, ctx.now).run(xa).flatMap(_ => ProposalInviteQueries.selectOne(id).runUnique(xa))

  override def cancelProposalInvite(id: Id)(implicit ctx: OrgaCtx): IO[ProposalInvite] =
    ProposalInviteQueries.cancel(id, ctx.user.id, ctx.now).run(xa).flatMap(_ => ProposalInviteQueries.selectOne(id).runUnique(xa))

  override def accept(invite: UserRequest.ProposalInvite)(implicit ctx: UserCtx): IO[Done] = for {
    _ <- ProposalInviteQueries.accept(invite.id, ctx.user.id, ctx.now).run(xa)
    _ <- proposalRepo.addSpeaker(invite.proposal)(ctx.user.id, invite.createdBy, ctx.now)
  } yield Done

  override def reject(invite: UserRequest.ProposalInvite)(implicit ctx: UserCtx): IO[Done] = ProposalInviteQueries.reject(invite.id, ctx.user.id, ctx.now).run(xa)

  override def listPendingInvites(proposal: Proposal.Id): IO[Seq[ProposalInvite]] = ProposalInviteQueries.selectAllPending(proposal).runList(xa)
}

object UserRequestRepoSql {
  private val _ = userRequestIdMeta // for intellij not remove DoobieUtils.Mappings import
  private val table = Tables.userRequests

  private[sql] def selectOne(id: UserRequest.Id): Select[UserRequest] =
    table.select[UserRequest](fr0"WHERE ur.id=$id")

  private[sql] def selectOnePending(group: Group.Id, req: UserRequest.Id, now: Instant): Select[UserRequest] =
    table.select[UserRequest](fr0"WHERE ur.id=$req AND ur.group_id=$group AND ur.accepted_at IS NULL AND ur.rejected_at IS NULL AND (ur.deadline IS NULL OR ur.deadline > $now)")

  private[sql] def selectPage(user: User.Id, params: Page.Params): SelectPage[UserRequest] =
    table.selectPage[UserRequest](params, fr0"WHERE ur.created_by=$user")

  private[sql] def selectAllPending(group: Group.Id, now: Instant): Select[UserRequest] =
    table.select[UserRequest](fr0"WHERE ur.group_id=$group AND ur.accepted_at IS NULL AND ur.rejected_at IS NULL AND (ur.deadline IS NULL OR ur.deadline > $now)")

  object AccountValidation {
    private val kind = "AccountValidation"
    private val fields = Seq("id", "kind", "email", "deadline", "created_at", "created_by", "accepted_at").map(n => Field(n, table.prefix))

    private[sql] def insert(elt: AccountValidationRequest): Insert[AccountValidationRequest] = {
      val values = fr0"${elt.id}, $kind, ${elt.email}, ${elt.deadline}, ${elt.createdAt}, ${elt.createdBy}, ${elt.acceptedAt}"
      table.insertPartial[AccountValidationRequest](fields, elt, _ => values)
    }

    private[sql] def accept(id: UserRequest.Id, now: Instant): Update = table.update(fr0"accepted_at=$now", where(id, now))

    private[sql] def selectOne(id: UserRequest.Id): Select[AccountValidationRequest] = table.select[AccountValidationRequest](fields.filter(_.name != "kind"), fr0"WHERE ur.id=$id AND ur.kind=$kind")

    private[sql] def selectPending(id: User.Id, now: Instant): Select[AccountValidationRequest] = table.select[AccountValidationRequest](fields.filter(_.name != "kind"), where(id, now))

    private def where(id: UserRequest.Id, now: Instant): Fragment = fr0"WHERE ur.id=$id AND ur.kind=$kind AND ur.deadline > $now AND ur.accepted_at IS NULL"

    private def where(id: User.Id, now: Instant): Fragment = fr0"WHERE ur.created_by=$id AND ur.kind=$kind AND ur.deadline > $now AND ur.accepted_at IS NULL"
  }

  object PasswordReset {
    private val kind = "PasswordReset"
    private val fields = Seq("id", "kind", "email", "deadline", "created_at", "accepted_at").map(n => Field(n, table.prefix))

    private[sql] def insert(elt: PasswordResetRequest): Insert[PasswordResetRequest] = {
      val values = fr0"${elt.id}, $kind, ${elt.email}, ${elt.deadline}, ${elt.createdAt}, ${elt.acceptedAt}"
      table.insertPartial[PasswordResetRequest](fields, elt, _ => values)
    }

    private[sql] def accept(id: UserRequest.Id, now: Instant): Update = table.update(fr0"accepted_at=$now", where(id, now))

    private[sql] def selectPending(id: UserRequest.Id, now: Instant): Select[PasswordResetRequest] = table.select[PasswordResetRequest](fields.filter(_.name != "kind"), where(id, now))

    private[sql] def selectPending(email: EmailAddress, now: Instant): Select[PasswordResetRequest] = table.select[PasswordResetRequest](fields.filter(_.name != "kind"), where(email, now))

    private def where(id: UserRequest.Id, now: Instant): Fragment = fr0"WHERE ur.id=$id AND ur.kind=$kind AND ur.deadline > $now AND ur.accepted_at IS NULL"

    private def where(email: EmailAddress, now: Instant): Fragment = fr0"WHERE ur.email=$email AND ur.kind=$kind AND ur.deadline > $now AND ur.accepted_at IS NULL"
  }

  object UserAskToJoinAGroup {
    private val kind = "UserAskToJoinAGroup"
    private val fields = Seq("id", "kind", "group_id", "created_at", "created_by", "accepted_at", "accepted_by", "rejected_at", "rejected_by", "canceled_at", "canceled_by").map(n => Field(n, table.prefix))
    private val selectFields = fields.filter(_.name != "kind")

    private[sql] def insert(elt: UserAskToJoinAGroupRequest): Insert[UserAskToJoinAGroupRequest] = {
      val values = fr0"${elt.id}, $kind, ${elt.group}, ${elt.createdAt}, ${elt.createdBy}, ${elt.accepted.map(_.date)}, ${elt.accepted.map(_.by)}, ${elt.rejected.map(_.date)}, ${elt.rejected.map(_.by)}, ${elt.canceled.map(_.date)}, ${elt.canceled.map(_.by)}"
      table.insertPartial[UserAskToJoinAGroupRequest](fields, elt, _ => values)
    }

    private[sql] def accept(id: UserRequest.Id, by: User.Id, now: Instant): Update = table.update(fr0"accepted_at=$now, accepted_by=$by", wherePending(id, now))

    private[sql] def reject(id: UserRequest.Id, by: User.Id, now: Instant): Update = table.update(fr0"rejected_at=$now, rejected_by=$by", wherePending(id, now))

    private[sql] def cancel(id: UserRequest.Id, by: User.Id, now: Instant): Update = table.update(fr0"canceled_at=$now, canceled_by=$by", wherePending(id, now))

    private[sql] def selectOnePending(group: Group.Id, id: UserRequest.Id): Select[UserAskToJoinAGroupRequest] =
      table.select[UserAskToJoinAGroupRequest](selectFields, fr0"WHERE ur.id=$id AND ur.kind=$kind AND ur.group_id=$group AND ur.accepted_at IS NULL AND ur.rejected_at IS NULL AND ur.canceled_at IS NULL")

    private[sql] def selectAllPending(user: User.Id): Select[UserAskToJoinAGroupRequest] =
      table.select[UserAskToJoinAGroupRequest](selectFields, fr0"WHERE ur.kind=$kind AND ur.created_by=$user AND ur.accepted_at IS NULL AND ur.rejected_at IS NULL AND ur.canceled_at IS NULL")

    private def wherePending(id: UserRequest.Id, now: Instant): Fragment = fr0"WHERE ur.id=$id AND ur.kind=$kind AND ur.accepted_at IS NULL AND ur.rejected_at IS NULL AND ur.canceled_at IS NULL"
  }

  object GroupInviteQueries {
    private val kind = "GroupInvite"
    private val fields = Seq("id", "kind", "group_id", "email", "created_at", "created_by", "accepted_at", "accepted_by", "rejected_at", "rejected_by", "canceled_at", "canceled_by").map(n => Field(n, table.prefix))
    private val selectFields = fields.filter(_.name != "kind")

    private[sql] def insert(elt: GroupInvite): Insert[GroupInvite] = {
      val values = fr0"${elt.id}, $kind, ${elt.group}, ${elt.email}, ${elt.createdAt}, ${elt.createdBy}, ${elt.accepted.map(_.date)}, ${elt.accepted.map(_.by)}, ${elt.rejected.map(_.date)}, ${elt.rejected.map(_.by)}, ${elt.canceled.map(_.date)}, ${elt.canceled.map(_.by)}"
      table.insertPartial[GroupInvite](fields, elt, _ => values)
    }

    private[sql] def accept(id: UserRequest.Id, by: User.Id, now: Instant): Update = table.update(fr0"accepted_at=$now, accepted_by=$by", wherePending(id, now))

    private[sql] def reject(id: UserRequest.Id, by: User.Id, now: Instant): Update = table.update(fr0"rejected_at=$now, rejected_by=$by", wherePending(id, now))

    private[sql] def cancel(id: UserRequest.Id, by: User.Id, now: Instant): Update = table.update(fr0"canceled_at=$now, canceled_by=$by", wherePending(id, now))

    private[sql] def selectOne(id: UserRequest.Id): Select[GroupInvite] = table.select[GroupInvite](selectFields, fr0"WHERE ur.id=$id")

    private[sql] def selectAllPending(group: Group.Id): Select[GroupInvite] =
      table.select[GroupInvite](selectFields, fr0"WHERE ur.kind=$kind AND ur.group_id=$group AND ur.accepted_at IS NULL AND ur.rejected_at IS NULL AND ur.canceled_at IS NULL")

    private def wherePending(id: UserRequest.Id, now: Instant): Fragment = fr0"WHERE ur.id=$id AND ur.kind=$kind AND ur.accepted_at IS NULL AND ur.rejected_at IS NULL AND ur.canceled_at IS NULL"
  }

  object TalkInviteQueries {
    private val kind = "TalkInvite"
    private val fields = Seq("id", "kind", "talk_id", "email", "created_at", "created_by", "accepted_at", "accepted_by", "rejected_at", "rejected_by", "canceled_at", "canceled_by").map(n => Field(n, table.prefix))
    private val selectFields = fields.filter(_.name != "kind")

    private[sql] def insert(elt: TalkInvite): Insert[TalkInvite] = {
      val values = fr0"${elt.id}, $kind, ${elt.talk}, ${elt.email}, ${elt.createdAt}, ${elt.createdBy}, ${elt.accepted.map(_.date)}, ${elt.accepted.map(_.by)}, ${elt.rejected.map(_.date)}, ${elt.rejected.map(_.by)}, ${elt.canceled.map(_.date)}, ${elt.canceled.map(_.by)}"
      table.insertPartial[TalkInvite](fields, elt, _ => values)
    }

    private[sql] def accept(id: UserRequest.Id, by: User.Id, now: Instant): Update = table.update(fr0"accepted_at=$now, accepted_by=$by", wherePending(id, now))

    private[sql] def reject(id: UserRequest.Id, by: User.Id, now: Instant): Update = table.update(fr0"rejected_at=$now, rejected_by=$by", wherePending(id, now))

    private[sql] def cancel(id: UserRequest.Id, by: User.Id, now: Instant): Update = table.update(fr0"canceled_at=$now, canceled_by=$by", wherePending(id, now))

    private[sql] def selectOne(id: UserRequest.Id): Select[TalkInvite] = table.select[TalkInvite](selectFields, fr0"WHERE ur.id=$id")

    private[sql] def selectAllPending(talk: Talk.Id): Select[TalkInvite] =
      table.select[TalkInvite](selectFields, fr0"WHERE ur.kind=$kind AND ur.talk_id=$talk AND ur.accepted_at IS NULL AND ur.rejected_at IS NULL AND ur.canceled_at IS NULL")

    private def wherePending(id: UserRequest.Id, now: Instant): Fragment = fr0"WHERE ur.id=$id AND ur.kind=$kind AND ur.accepted_at IS NULL AND ur.rejected_at IS NULL AND ur.canceled_at IS NULL"
  }

  object ProposalInviteQueries {
    private val kind = "ProposalInvite"
    private val fields = Seq("id", "kind", "proposal_id", "email", "created_at", "created_by", "accepted_at", "accepted_by", "rejected_at", "rejected_by", "canceled_at", "canceled_by").map(n => Field(n, table.prefix))
    private val selectFields = fields.filter(_.name != "kind")

    private[sql] def insert(elt: ProposalInvite): Insert[ProposalInvite] = {
      val values = fr0"${elt.id}, $kind, ${elt.proposal}, ${elt.email}, ${elt.createdAt}, ${elt.createdBy}, ${elt.accepted.map(_.date)}, ${elt.accepted.map(_.by)}, ${elt.rejected.map(_.date)}, ${elt.rejected.map(_.by)}, ${elt.canceled.map(_.date)}, ${elt.canceled.map(_.by)}"
      table.insertPartial[ProposalInvite](fields, elt, _ => values)
    }

    private[sql] def accept(id: UserRequest.Id, by: User.Id, now: Instant): Update = table.update(fr0"accepted_at=$now, accepted_by=$by", wherePending(id, now))

    private[sql] def reject(id: UserRequest.Id, by: User.Id, now: Instant): Update = table.update(fr0"rejected_at=$now, rejected_by=$by", wherePending(id, now))

    private[sql] def cancel(id: UserRequest.Id, by: User.Id, now: Instant): Update = table.update(fr0"canceled_at=$now, canceled_by=$by", wherePending(id, now))

    private[sql] def selectOne(id: UserRequest.Id): Select[ProposalInvite] = table.select[ProposalInvite](selectFields, fr0"WHERE ur.id=$id")

    private[sql] def selectAllPending(proposal: Proposal.Id): Select[ProposalInvite] =
      table.select[ProposalInvite](selectFields, fr0"WHERE ur.kind=$kind AND ur.proposal_id=$proposal AND ur.accepted_at IS NULL AND ur.rejected_at IS NULL AND ur.canceled_at IS NULL")

    private def wherePending(id: UserRequest.Id, now: Instant): Fragment = fr0"WHERE ur.id=$id AND ur.kind=$kind AND ur.accepted_at IS NULL AND ur.rejected_at IS NULL AND ur.canceled_at IS NULL"
  }

}
