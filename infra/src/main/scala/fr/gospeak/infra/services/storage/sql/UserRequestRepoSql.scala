package fr.gospeak.infra.services.storage.sql

import java.time.Instant

import cats.effect.IO
import doobie.implicits._
import doobie.util.Read
import doobie.util.fragment.Fragment
import fr.gospeak.core.domain.UserRequest._
import fr.gospeak.core.domain._
import fr.gospeak.core.services.storage.UserRequestRepo
import fr.gospeak.infra.services.storage.sql.UserRequestRepoSql._
import fr.gospeak.infra.services.storage.sql.utils.GenericRepo
import fr.gospeak.infra.services.storage.sql.utils.DoobieUtils.Mappings._
import fr.gospeak.infra.services.storage.sql.utils.DoobieUtils.{Field, Insert, Select, SelectPage, Update}
import fr.gospeak.libs.scalautils.Extensions._
import fr.gospeak.libs.scalautils.domain.{Done, EmailAddress, Page}

class UserRequestRepoSql(groupRepo: GroupRepoSql,
                         talkRepo: TalkRepoSql,
                         proposalRepo: ProposalRepoSql,
                         protected[sql] val xa: doobie.Transactor[IO]) extends GenericRepo with UserRequestRepo {
  override def find(id: UserRequest.Id): IO[Option[UserRequest]] = selectOne(id).runOption(xa)

  override def list(user: User.Id, params: Page.Params): IO[Page[UserRequest]] = selectPage(user, params).run(xa)

  override def listPendingGroupRequests(group: Group.Id, now: Instant): IO[Seq[UserRequest]] = selectAllPending(group, now).runList(xa)

  // override def findPending(group: Group.Id, req: UserRequest.Id, now: Instant): IO[Option[UserRequest]] = selectOnePending(group, req, now).runOption(xa)

  override def findPendingUserToJoinAGroup(group: Group.Id, req: UserRequest.Id): IO[Option[UserAskToJoinAGroupRequest]] =
    UserAskToJoinAGroup.selectOnePending(group, req).runOption(xa)

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


  override def createUserAskToJoinAGroup(user: User.Id, group: Group.Id, now: Instant): IO[UserAskToJoinAGroupRequest] =
    UserAskToJoinAGroup.insert(UserAskToJoinAGroupRequest(UserRequest.Id.generate(), group, now, user, None, None, None)).run(xa)

  override def acceptUserToJoinAGroup(req: UserAskToJoinAGroupRequest, by: User.Id, now: Instant): IO[Done] = for {
    _ <- UserRequestRepoSql.accept(req.id, UserAskToJoinAGroup.kind, by, now).run(xa)
    _ <- groupRepo.addOwner(req.group)(req.createdBy, by, now)
  } yield Done

  override def rejectUserToJoinAGroup(req: UserAskToJoinAGroupRequest, by: User.Id, now: Instant): IO[Done] = UserRequestRepoSql.reject(req.id, UserAskToJoinAGroup.kind, by, now).run(xa)

  override def listPendingUserToJoinAGroupRequests(user: User.Id): IO[Seq[UserAskToJoinAGroupRequest]] = UserAskToJoinAGroup.selectAllPending(user).runList(xa)


  override def invite(group: Group.Id, email: EmailAddress, by: User.Id, now: Instant): IO[GroupInvite] =
    GroupInviteQueries.insert(GroupInvite(UserRequest.Id.generate(), group, email, now, by, None, None, None)).run(xa)

  override def cancelGroupInvite(id: Id, by: User.Id, now: Instant): IO[GroupInvite] =
    UserRequestRepoSql.cancel(id, GroupInviteQueries.kind, by, now).run(xa)
      .flatMap(_ => UserRequestRepoSql.selectOne[GroupInvite](id, GroupInviteQueries.selectFields).runUnique(xa))

  override def accept(invite: UserRequest.GroupInvite, by: User.Id, now: Instant): IO[Done] = for {
    _ <- UserRequestRepoSql.accept(invite.id, GroupInviteQueries.kind, by, now).run(xa)
    _ <- groupRepo.addOwner(invite.group)(by, invite.createdBy, now)
  } yield Done

  override def reject(invite: UserRequest.GroupInvite, by: User.Id, now: Instant): IO[Done] = UserRequestRepoSql.reject(invite.id, GroupInviteQueries.kind, by, now).run(xa)

  override def listPendingInvites(group: Group.Id): IO[Seq[GroupInvite]] = GroupInviteQueries.selectAllPending(group).runList(xa)


  override def invite(talk: Talk.Id, email: EmailAddress, by: User.Id, now: Instant): IO[TalkInvite] =
    TalkInviteQueries.insert(TalkInvite(UserRequest.Id.generate(), talk, email, now, by, None, None, None)).run(xa)

  override def cancelTalkInvite(id: UserRequest.Id, by: User.Id, now: Instant): IO[TalkInvite] =
    UserRequestRepoSql.cancel(id, TalkInviteQueries.kind, by, now).run(xa)
      .flatMap(_ => UserRequestRepoSql.selectOne[TalkInvite](id, TalkInviteQueries.selectFields).runUnique(xa))

  override def accept(invite: UserRequest.TalkInvite, by: User.Id, now: Instant): IO[Done] = for {
    _ <- UserRequestRepoSql.accept(invite.id, TalkInviteQueries.kind, by, now).run(xa)
    _ <- talkRepo.addSpeaker(invite.talk)(by, invite.createdBy, now)
  } yield Done

  override def reject(invite: UserRequest.TalkInvite, by: User.Id, now: Instant): IO[Done] = UserRequestRepoSql.reject(invite.id, TalkInviteQueries.kind, by, now).run(xa)

  override def listPendingInvites(talk: Talk.Id): IO[Seq[TalkInvite]] = TalkInviteQueries.selectAllPending(talk).runList(xa)


  override def invite(proposal: Proposal.Id, email: EmailAddress, by: User.Id, now: Instant): IO[ProposalInvite] =
    ProposalInviteQueries.insert(ProposalInvite(UserRequest.Id.generate(), proposal, email, now, by, None, None, None)).run(xa)

  override def cancelProposalInvite(id: UserRequest.Id, by: User.Id, now: Instant): IO[ProposalInvite] =
    UserRequestRepoSql.cancel(id, ProposalInviteQueries.kind, by, now).run(xa)
      .flatMap(_ => UserRequestRepoSql.selectOne[ProposalInvite](id, ProposalInviteQueries.selectFields).runUnique(xa))

  override def accept(invite: UserRequest.ProposalInvite, by: User.Id, now: Instant): IO[Done] = for {
    _ <- UserRequestRepoSql.accept(invite.id, ProposalInviteQueries.kind, by, now).run(xa)
    _ <- proposalRepo.addSpeaker(invite.proposal)(by, invite.createdBy, now)
  } yield Done

  override def reject(invite: UserRequest.ProposalInvite, by: User.Id, now: Instant): IO[Done] = UserRequestRepoSql.reject(invite.id, ProposalInviteQueries.kind, by, now).run(xa)

  override def listPendingInvites(proposal: Proposal.Id): IO[Seq[ProposalInvite]] = ProposalInviteQueries.selectAllPending(proposal).runList(xa)


  override def createProposal(cfp: Cfp.Id, event: Option[Event.Id], email: EmailAddress, payload: ProposalCreation.Payload, by: User.Id, now: Instant): IO[ProposalCreation] =
    ProposalCreationQueries.insert(ProposalCreation(UserRequest.Id.generate(), cfp, event, email, payload, now, by, None, None, None)).run(xa)

  override def cancelProposalCreation(id: UserRequest.Id, by: User.Id, now: Instant): IO[ProposalCreation] =
    UserRequestRepoSql.cancel(id, ProposalCreationQueries.kind, by, now).run(xa)
      .flatMap(_ => UserRequestRepoSql.selectOne[ProposalCreation](id, ProposalCreationQueries.selectFields).runUnique(xa))

  override def accept(invite: UserRequest.ProposalCreation, by: User.Id, now: Instant): IO[Done] = for {
    _ <- UserRequestRepoSql.accept(invite.id, ProposalCreationQueries.kind, by, now).run(xa)
    // TODO _ <- proposalRepo.addSpeaker(invite.proposal)(by, invite.createdBy, now)
  } yield Done

  override def reject(invite: UserRequest.ProposalCreation, by: Option[User.Id], now: Instant): IO[Done] = UserRequestRepoSql.reject(invite.id, ProposalCreationQueries.kind, by, now).run(xa)

  override def listPendingProposalCreations(cfp: Cfp.Id): IO[Seq[ProposalCreation]] = ProposalCreationQueries.selectAllPending(cfp).runList(xa)

  override def listPendingProposalCreations(event: Event.Id): IO[Seq[ProposalCreation]] = ProposalCreationQueries.selectAllPending(event).runList(xa)
}

object UserRequestRepoSql {
  private val _ = userRequestIdMeta // for intellij not remove DoobieUtils.Mappings import
  private val table = Tables.requests

  private[sql] def accept(id: UserRequest.Id, kind: String, by: User.Id, now: Instant): Update = table.update(fr0"accepted=$now, accepted_by=$by", wherePending(id, kind, now))

  private[sql] def reject(id: UserRequest.Id, kind: String, by: User.Id, now: Instant): Update = table.update(fr0"rejected=$now, rejected_by=$by", wherePending(id, kind, now))

  private[sql] def reject(id: UserRequest.Id, kind: String, by: Option[User.Id], now: Instant): Update = table.update(fr0"rejected=$now, rejected_by=$by", wherePending(id, kind, now))

  private[sql] def cancel(id: UserRequest.Id, kind: String, by: User.Id, now: Instant): Update = table.update(fr0"canceled=$now, canceled_by=$by", wherePending(id, kind, now))

  private[sql] def selectOne[A: Read](id: UserRequest.Id, fields: Seq[Field]): Select[A] = table.select[A](fields, fr0"WHERE r.id=$id")

  private[sql] def selectOne(id: UserRequest.Id): Select[UserRequest] =
    table.select[UserRequest](fr0"WHERE r.id=$id")

  private[sql] def selectOnePending(group: Group.Id, req: UserRequest.Id, now: Instant): Select[UserRequest] =
    table.select[UserRequest](fr0"WHERE r.id=$req AND r.group_id=$group AND r.accepted IS NULL AND r.rejected IS NULL AND (r.deadline IS NULL OR r.deadline > $now)")

  private[sql] def selectPage(user: User.Id, params: Page.Params): SelectPage[UserRequest] =
    table.selectPage[UserRequest](params, fr0"WHERE r.created_by=$user")

  private[sql] def selectAllPending(group: Group.Id, now: Instant): Select[UserRequest] =
    table.select[UserRequest](fr0"WHERE r.group_id=$group AND r.accepted IS NULL AND r.rejected IS NULL AND (r.deadline IS NULL OR r.deadline > $now)")

  private def wherePending(id: UserRequest.Id, kind: String, now: Instant): Fragment = fr0"WHERE r.id=$id AND r.kind=$kind AND r.accepted IS NULL AND r.rejected IS NULL AND r.canceled IS NULL"

  object AccountValidation {
    private[sql] val kind = "AccountValidation"
    private[sql] val fields = Seq("id", "kind", "email", "deadline", "created", "created_by", "accepted").map(n => Field(n, table.prefix))

    private[sql] def insert(elt: AccountValidationRequest): Insert[AccountValidationRequest] = {
      val values = fr0"${elt.id}, $kind, ${elt.email}, ${elt.deadline}, ${elt.created}, ${elt.createdBy}, ${elt.accepted}"
      table.insertPartial[AccountValidationRequest](fields, elt, _ => values)
    }

    private[sql] def accept(id: UserRequest.Id, now: Instant): Update = table.update(fr0"accepted=$now", where(id, now))

    private[sql] def selectOne(id: UserRequest.Id): Select[AccountValidationRequest] = table.select[AccountValidationRequest](fields.filter(_.name != "kind"), fr0"WHERE r.id=$id AND r.kind=$kind")

    private[sql] def selectPending(id: User.Id, now: Instant): Select[AccountValidationRequest] = table.select[AccountValidationRequest](fields.filter(_.name != "kind"), where(id, now))

    private def where(id: UserRequest.Id, now: Instant): Fragment = fr0"WHERE r.id=$id AND r.kind=$kind AND r.deadline > $now AND r.accepted IS NULL"

    private def where(id: User.Id, now: Instant): Fragment = fr0"WHERE r.created_by=$id AND r.kind=$kind AND r.deadline > $now AND r.accepted IS NULL"
  }

  object PasswordReset {
    private[sql] val kind = "PasswordReset"
    private[sql] val fields = Seq("id", "kind", "email", "deadline", "created", "accepted").map(n => Field(n, table.prefix))

    private[sql] def insert(elt: PasswordResetRequest): Insert[PasswordResetRequest] = {
      val values = fr0"${elt.id}, $kind, ${elt.email}, ${elt.deadline}, ${elt.created}, ${elt.accepted}"
      table.insertPartial[PasswordResetRequest](fields, elt, _ => values)
    }

    private[sql] def accept(id: UserRequest.Id, now: Instant): Update = table.update(fr0"accepted=$now", where(id, now))

    private[sql] def selectPending(id: UserRequest.Id, now: Instant): Select[PasswordResetRequest] = table.select[PasswordResetRequest](fields.filter(_.name != "kind"), where(id, now))

    private[sql] def selectPending(email: EmailAddress, now: Instant): Select[PasswordResetRequest] = table.select[PasswordResetRequest](fields.filter(_.name != "kind"), where(email, now))

    private def where(id: UserRequest.Id, now: Instant): Fragment = fr0"WHERE r.id=$id AND r.kind=$kind AND r.deadline > $now AND r.accepted IS NULL"

    private def where(email: EmailAddress, now: Instant): Fragment = fr0"WHERE r.email=$email AND r.kind=$kind AND r.deadline > $now AND r.accepted IS NULL"
  }

  object UserAskToJoinAGroup {
    private[sql] val kind = "UserAskToJoinAGroup"
    private val fields = Seq("id", "kind", "group_id", "created", "created_by", "accepted", "accepted_by", "rejected", "rejected_by", "canceled", "canceled_by").map(n => Field(n, table.prefix))
    private[sql] val selectFields = fields.filter(_.name != "kind")

    private[sql] def insert(elt: UserAskToJoinAGroupRequest): Insert[UserAskToJoinAGroupRequest] = {
      val values = fr0"${elt.id}, $kind, ${elt.group}, ${elt.created}, ${elt.createdBy}, ${elt.accepted.map(_.date)}, ${elt.accepted.map(_.by)}, ${elt.rejected.map(_.date)}, ${elt.rejected.map(_.by)}, ${elt.canceled.map(_.date)}, ${elt.canceled.map(_.by)}"
      table.insertPartial[UserAskToJoinAGroupRequest](fields, elt, _ => values)
    }

    private[sql] def selectOnePending(group: Group.Id, id: UserRequest.Id): Select[UserAskToJoinAGroupRequest] =
      table.select[UserAskToJoinAGroupRequest](selectFields, fr0"WHERE r.id=$id AND r.kind=$kind AND r.group_id=$group AND r.accepted IS NULL AND r.rejected IS NULL AND r.canceled IS NULL")

    private[sql] def selectAllPending(user: User.Id): Select[UserAskToJoinAGroupRequest] =
      table.select[UserAskToJoinAGroupRequest](selectFields, fr0"WHERE r.kind=$kind AND r.created_by=$user AND r.accepted IS NULL AND r.rejected IS NULL AND r.canceled IS NULL")
  }

  object GroupInviteQueries {
    private[sql] val kind = "GroupInvite"
    private val fields = Seq("id", "kind", "group_id", "email", "created", "created_by", "accepted", "accepted_by", "rejected", "rejected_by", "canceled", "canceled_by").map(n => Field(n, table.prefix))
    private[sql] val selectFields = fields.filter(_.name != "kind")

    private[sql] def insert(elt: GroupInvite): Insert[GroupInvite] = {
      val values = fr0"${elt.id}, $kind, ${elt.group}, ${elt.email}, ${elt.created}, ${elt.createdBy}, ${elt.accepted.map(_.date)}, ${elt.accepted.map(_.by)}, ${elt.rejected.map(_.date)}, ${elt.rejected.map(_.by)}, ${elt.canceled.map(_.date)}, ${elt.canceled.map(_.by)}"
      table.insertPartial[GroupInvite](fields, elt, _ => values)
    }

    private[sql] def selectAllPending(group: Group.Id): Select[GroupInvite] =
      table.select[GroupInvite](selectFields, fr0"WHERE r.kind=$kind AND r.group_id=$group AND r.accepted IS NULL AND r.rejected IS NULL AND r.canceled IS NULL")
  }

  object TalkInviteQueries {
    private[sql] val kind = "TalkInvite"
    private val fields = Seq("id", "kind", "talk_id", "email", "created", "created_by", "accepted", "accepted_by", "rejected", "rejected_by", "canceled", "canceled_by").map(n => Field(n, table.prefix))
    private[sql] val selectFields = fields.filter(_.name != "kind")

    private[sql] def insert(elt: TalkInvite): Insert[TalkInvite] = {
      val values = fr0"${elt.id}, $kind, ${elt.talk}, ${elt.email}, ${elt.created}, ${elt.createdBy}, ${elt.accepted.map(_.date)}, ${elt.accepted.map(_.by)}, ${elt.rejected.map(_.date)}, ${elt.rejected.map(_.by)}, ${elt.canceled.map(_.date)}, ${elt.canceled.map(_.by)}"
      table.insertPartial[TalkInvite](fields, elt, _ => values)
    }

    private[sql] def selectAllPending(talk: Talk.Id): Select[TalkInvite] =
      table.select[TalkInvite](selectFields, fr0"WHERE r.kind=$kind AND r.talk_id=$talk AND r.accepted IS NULL AND r.rejected IS NULL AND r.canceled IS NULL")
  }

  object ProposalInviteQueries {
    private[sql] val kind = "ProposalInvite"
    private val fields = Seq("id", "kind", "proposal_id", "email", "created", "created_by", "accepted", "accepted_by", "rejected", "rejected_by", "canceled", "canceled_by").map(n => Field(n, table.prefix))
    private[sql] val selectFields = fields.filter(_.name != "kind")

    private[sql] def insert(elt: ProposalInvite): Insert[ProposalInvite] = {
      val values = fr0"${elt.id}, $kind, ${elt.proposal}, ${elt.email}, ${elt.created}, ${elt.createdBy}, ${elt.accepted.map(_.date)}, ${elt.accepted.map(_.by)}, ${elt.rejected.map(_.date)}, ${elt.rejected.map(_.by)}, ${elt.canceled.map(_.date)}, ${elt.canceled.map(_.by)}"
      table.insertPartial[ProposalInvite](fields, elt, _ => values)
    }

    private[sql] def selectAllPending(proposal: Proposal.Id): Select[ProposalInvite] =
      table.select[ProposalInvite](selectFields, fr0"WHERE r.kind=$kind AND r.proposal_id=$proposal AND r.accepted IS NULL AND r.rejected IS NULL AND r.canceled IS NULL")
  }

  object ProposalCreationQueries {
    private[sql] val kind = "ProposalCreation"
    private val fields = Seq("id", "kind", "cfp_id", "event_id", "email", "payload", "created", "created_by", "accepted", "accepted_by", "rejected", "rejected_by", "canceled", "canceled_by").map(n => Field(n, table.prefix))
    private[sql] val selectFields = fields.filter(_.name != "kind")

    private[sql] def insert(e: ProposalCreation): Insert[ProposalCreation] = {
      val values = fr0"${e.id}, $kind, ${e.cfp}, ${e.event}, ${e.email}, ${e.payload}, ${e.created}, ${e.createdBy}, ${e.accepted.map(_.date)}, ${e.accepted.map(_.by)}, ${e.rejected.map(_.date)}, ${e.rejected.flatMap(_.by)}, ${e.canceled.map(_.date)}, ${e.canceled.map(_.by)}"
      table.insertPartial[ProposalCreation](fields, e, _ => values)
    }

    private[sql] def selectAllPending(cfp: Cfp.Id): Select[ProposalCreation] =
      table.select[ProposalCreation](selectFields, fr0"WHERE r.kind=$kind AND r.cfp_id=$cfp AND r.accepted IS NULL AND r.rejected IS NULL AND r.canceled IS NULL")

    private[sql] def selectAllPending(event: Event.Id): Select[ProposalCreation] =
      table.select[ProposalCreation](selectFields, fr0"WHERE r.kind=$kind AND r.event_id=$event AND r.accepted IS NULL AND r.rejected IS NULL AND r.canceled IS NULL")
  }

}
