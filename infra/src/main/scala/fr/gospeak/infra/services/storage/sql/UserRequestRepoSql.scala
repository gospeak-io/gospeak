package fr.gospeak.infra.services.storage.sql

import java.time.Instant

import cats.effect.IO
import doobie.implicits._
import doobie.util.fragment.Fragment
import fr.gospeak.core.domain.UserRequest._
import fr.gospeak.core.domain.{Group, Proposal, Talk, User, UserRequest}
import fr.gospeak.core.services.storage.UserRequestRepo
import fr.gospeak.infra.services.storage.sql.UserRequestRepoSql._
import fr.gospeak.infra.services.storage.sql.utils.GenericRepo
import fr.gospeak.infra.utils.DoobieUtils.Fragments._
import fr.gospeak.infra.utils.DoobieUtils.Mappings._
import fr.gospeak.infra.utils.DoobieUtils.Queries
import fr.gospeak.libs.scalautils.Extensions._
import fr.gospeak.libs.scalautils.domain.{Done, EmailAddress, Page}

class UserRequestRepoSql(groupRepo: GroupRepoSql,
                         talkRepo: TalkRepoSql,
                         proposalRepo: ProposalRepoSql,
                         protected[sql] val xa: doobie.Transactor[IO]) extends GenericRepo with UserRequestRepo {
  override def find(id: UserRequest.Id): IO[Option[UserRequest]] =
    run(selectOne(id).option)

  override def list(user: User.Id, params: Page.Params): IO[Page[UserRequest]] =
    run(Queries.selectPage(selectPage(user, _), params))

  override def listPendingGroupRequests(group: Group.Id, now: Instant): IO[Seq[UserRequest]] =
    run(selectAllPending(group, now).to[List])

  /* def findPending(group: Group.Id, req: UserRequest.Id, now: Instant): IO[Option[UserRequest]] =
    run(selectOnePending(group, req, now).option) */

  override def findPendingUserToJoinAGroup(group: Group.Id, req: UserRequest.Id): IO[Option[UserAskToJoinAGroupRequest]] =
    run(UserAskToJoinAGroup.selectOnePending(group, req).option)

  override def createAccountValidationRequest(email: EmailAddress, user: User.Id, now: Instant): IO[AccountValidationRequest] =
    run(AccountValidation.insert, AccountValidationRequest(UserRequest.Id.generate(), email, now.plus(Timeout.accountValidation), now, user, None))

  override def validateAccount(id: UserRequest.Id, email: EmailAddress, now: Instant): IO[Done] = for {
    _ <- run(AccountValidation.accept(id, now))
    _ <- run(UserRepoSql.validateAccount(email, now))
  } yield Done

  override def findPendingAccountValidationRequest(id: UserRequest.Id, now: Instant): IO[Option[AccountValidationRequest]] =
    run(AccountValidation.selectPending(id, now).option)

  override def findPendingAccountValidationRequest(id: User.Id, now: Instant): IO[Option[AccountValidationRequest]] =
    run(AccountValidation.selectPending(id, now).option)


  override def createPasswordResetRequest(email: EmailAddress, now: Instant): IO[PasswordResetRequest] =
    run(PasswordReset.insert, PasswordResetRequest(UserRequest.Id.generate(), email, now, now.plus(Timeout.passwordReset), None))

  override def resetPassword(passwordReset: PasswordResetRequest, credentials: User.Credentials, now: Instant): IO[Done] = for {
    _ <- run(PasswordReset.accept(passwordReset.id, now))
    _ <- run(UserRepoSql.updateCredentials(credentials.login)(credentials.pass))
    _ <- run(UserRepoSql.validateAccount(passwordReset.email, now))
  } yield Done

  override def findPendingPasswordResetRequest(id: UserRequest.Id, now: Instant): IO[Option[PasswordResetRequest]] =
    run(PasswordReset.selectPending(id, now).option)

  override def findPendingPasswordResetRequest(email: EmailAddress, now: Instant): IO[Option[PasswordResetRequest]] =
    run(PasswordReset.selectPending(email, now).option)


  override def createUserAskToJoinAGroup(user: User.Id, group: Group.Id, now: Instant): IO[UserAskToJoinAGroupRequest] =
    run(UserAskToJoinAGroup.insert, UserAskToJoinAGroupRequest(UserRequest.Id.generate(), group, now, user, None, None, None))

  override def acceptUserToJoinAGroup(req: UserAskToJoinAGroupRequest, by: User.Id, now: Instant): IO[Done] = for {
    _ <- run(UserAskToJoinAGroup.accept(req.id, by, now))
    _ <- groupRepo.addOwner(req.group)(req.createdBy, by, now)
  } yield Done

  override def rejectUserToJoinAGroup(req: UserAskToJoinAGroupRequest, by: User.Id, now: Instant): IO[Done] =
    run(UserAskToJoinAGroup.reject(req.id, by, now))

  override def listPendingUserToJoinAGroupRequests(user: User.Id): IO[Seq[UserAskToJoinAGroupRequest]] =
    run(UserAskToJoinAGroup.selectAllPending(user).to[List])


  override def invite(group: Group.Id, email: EmailAddress, by: User.Id, now: Instant): IO[GroupInvite] =
    run(GroupInviteQueries.insert, GroupInvite(UserRequest.Id.generate(), group, email, now, by, None, None, None))

  override def cancelGroupInvite(id: Id, by: User.Id, now: Instant): IO[GroupInvite] =
    run(GroupInviteQueries.cancel(id, by, now)).flatMap(_ => run(GroupInviteQueries.selectOne(id).unique))

  override def accept(invite: UserRequest.GroupInvite, by: User.Id, now: Instant): IO[Done] = for {
    _ <- run(GroupInviteQueries.accept(invite.id, by, now))
    _ <- groupRepo.addOwner(invite.group)(by, invite.createdBy, now)
  } yield Done

  override def reject(invite: UserRequest.GroupInvite, by: User.Id, now: Instant): IO[Done] =
    run(GroupInviteQueries.reject(invite.id, by, now))

  override def listPendingInvites(group: Group.Id): IO[Seq[GroupInvite]] =
    run(GroupInviteQueries.selectAllPending(group).to[List])


  override def invite(talk: Talk.Id, email: EmailAddress, by: User.Id, now: Instant): IO[TalkInvite] =
    run(TalkInviteQueries.insert, TalkInvite(UserRequest.Id.generate(), talk, email, now, by, None, None, None))

  override def cancelTalkInvite(id: UserRequest.Id, by: User.Id, now: Instant): IO[TalkInvite] =
    run(TalkInviteQueries.cancel(id, by, now)).flatMap(_ => run(TalkInviteQueries.selectOne(id).unique))

  override def accept(invite: UserRequest.TalkInvite, by: User.Id, now: Instant): IO[Done] = for {
    _ <- run(TalkInviteQueries.accept(invite.id, by, now))
    _ <- talkRepo.addSpeaker(invite.talk)(by, invite.createdBy, now)
  } yield Done

  override def reject(invite: UserRequest.TalkInvite, by: User.Id, now: Instant): IO[Done] =
    run(TalkInviteQueries.reject(invite.id, by, now))

  override def listPendingInvites(talk: Talk.Id): IO[Seq[TalkInvite]] =
    run(TalkInviteQueries.selectAllPending(talk).to[List])


  override def invite(proposal: Proposal.Id, email: EmailAddress, by: User.Id, now: Instant): IO[ProposalInvite] =
    run(ProposalInviteQueries.insert, ProposalInvite(UserRequest.Id.generate(), proposal, email, now, by, None, None, None))

  override def cancelProposalInvite(id: Id, by: User.Id, now: Instant): IO[ProposalInvite] =
    run(ProposalInviteQueries.cancel(id, by, now)).flatMap(_ => run(ProposalInviteQueries.selectOne(id).unique))

  override def accept(invite: UserRequest.ProposalInvite, by: User.Id, now: Instant): IO[Done] = for {
    _ <- run(ProposalInviteQueries.accept(invite.id, by, now))
    _ <- proposalRepo.addSpeaker(invite.proposal)(by, invite.createdBy, now)
  } yield Done

  override def reject(invite: UserRequest.ProposalInvite, by: User.Id, now: Instant): IO[Done] =
    run(ProposalInviteQueries.reject(invite.id, by, now))

  override def listPendingInvites(proposal: Proposal.Id): IO[Seq[ProposalInvite]] =
    run(ProposalInviteQueries.selectAllPending(proposal).to[List])
}

object UserRequestRepoSql {
  private val _ = userRequestIdMeta // for intellij not remove DoobieUtils.Mappings import
  private val table = "requests"
  private val fields = Seq("id", "kind", "group_id", "talk_id", "proposal_id", "email", "deadline", "created", "created_by", "accepted", "accepted_by", "rejected", "rejected_by", "canceled", "canceled_by")
  private val tableFr: Fragment = Fragment.const0(table)
  private val fieldsFr: Fragment = Fragment.const0(fields.mkString(", "))
  private val searchFields = Seq("id", "email", "group_id", "created_by")
  private val defaultSort = Page.OrderBy("-created")

  private[sql] def selectOne(id: UserRequest.Id): doobie.Query0[UserRequest] =
    buildSelect(tableFr, fieldsFr, fr0"WHERE id=$id").query[UserRequest]

  private[sql] def selectOnePending(group: Group.Id, req: UserRequest.Id, now: Instant): doobie.Query0[UserRequest] =
    buildSelect(tableFr, fieldsFr, fr0"WHERE id=$req AND group_id=$group AND accepted IS NULL AND rejected IS NULL AND (deadline IS NULL OR deadline > $now)").query[UserRequest]

  private[sql] def selectPage(user: User.Id, params: Page.Params): (doobie.Query0[UserRequest], doobie.Query0[Long]) = {
    val page = paginate(params, searchFields, defaultSort, Some(fr0"WHERE created_by=$user"))
    (buildSelect(tableFr, fieldsFr, page.all).query[UserRequest], buildSelect(tableFr, fr0"count(*)", page.where).query[Long])
  }

  private[sql] def selectAllPending(group: Group.Id, now: Instant): doobie.Query0[UserRequest] =
    buildSelect(tableFr, fieldsFr, fr0"WHERE group_id=$group AND accepted IS NULL AND rejected IS NULL AND (deadline IS NULL OR deadline > $now)").query[UserRequest]

  object AccountValidation {
    private val kind = "AccountValidation"
    private val fields = Seq("id", "kind", "email", "deadline", "created", "created_by", "accepted")
    private val fieldsFr: Fragment = Fragment.const0(fields.mkString(", "))

    private[sql] def insert(elt: AccountValidationRequest): doobie.Update0 = {
      val values = fr0"${elt.id}, $kind, ${elt.email}, ${elt.deadline}, ${elt.created}, ${elt.createdBy}, ${elt.accepted}"
      buildInsert(tableFr, fieldsFr, values).update
    }

    private[sql] def accept(id: UserRequest.Id, now: Instant): doobie.Update0 =
      buildUpdate(tableFr, fr0"accepted=$now", where(id, now)).update

    private[sql] def selectPending(id: UserRequest.Id, now: Instant): doobie.Query0[AccountValidationRequest] =
      buildSelect(tableFr, Fragment.const0(fields.filter(_ != "kind").mkString(", ")), where(id, now)).query[AccountValidationRequest]

    private[sql] def selectPending(id: User.Id, now: Instant): doobie.Query0[AccountValidationRequest] =
      buildSelect(tableFr, Fragment.const0(fields.filter(_ != "kind").mkString(", ")), where(id, now)).query[AccountValidationRequest]

    private def where(id: UserRequest.Id, now: Instant): Fragment = fr0"WHERE id=$id AND kind=$kind AND deadline > $now AND accepted IS NULL"

    private def where(id: User.Id, now: Instant): Fragment = fr0"WHERE created_by=$id AND kind=$kind AND deadline > $now AND accepted IS NULL"
  }

  object PasswordReset {
    private val kind = "PasswordReset"
    private val fields = Seq("id", "kind", "email", "deadline", "created", "accepted")
    private val fieldsFr: Fragment = Fragment.const0(fields.mkString(", "))

    private[sql] def insert(elt: PasswordResetRequest): doobie.Update0 = {
      val values = fr0"${elt.id}, $kind, ${elt.email}, ${elt.deadline}, ${elt.created}, ${elt.accepted}"
      buildInsert(tableFr, fieldsFr, values).update
    }

    private[sql] def accept(id: UserRequest.Id, now: Instant): doobie.Update0 =
      buildUpdate(tableFr, fr0"accepted=$now", where(id, now)).update

    private[sql] def selectPending(id: UserRequest.Id, now: Instant): doobie.Query0[PasswordResetRequest] =
      buildSelect(tableFr, Fragment.const0(fields.filter(_ != "kind").mkString(", ")), where(id, now)).query[PasswordResetRequest]

    private[sql] def selectPending(email: EmailAddress, now: Instant): doobie.Query0[PasswordResetRequest] =
      buildSelect(tableFr, Fragment.const0(fields.filter(_ != "kind").mkString(", ")), where(email, now)).query[PasswordResetRequest]

    private def where(id: UserRequest.Id, now: Instant): Fragment = fr0"WHERE id=$id AND kind=$kind AND deadline > $now AND accepted IS NULL"

    private def where(email: EmailAddress, now: Instant): Fragment = fr0"WHERE email=$email AND kind=$kind AND deadline > $now AND accepted IS NULL"
  }

  object UserAskToJoinAGroup {
    private val kind = "UserAskToJoinAGroup"
    private val fields = Seq("id", "kind", "group_id", "created", "created_by", "accepted", "accepted_by", "rejected", "rejected_by", "canceled", "canceled_by")
    private val fieldsFr: Fragment = Fragment.const0(fields.mkString(", "))
    private val fieldsFrSelect: Fragment = Fragment.const0(fields.filter(_ != "kind").mkString(", "))

    private[sql] def insert(elt: UserAskToJoinAGroupRequest): doobie.Update0 = {
      val values = fr0"${elt.id}, $kind, ${elt.group}, ${elt.created}, ${elt.createdBy}, ${elt.accepted.map(_.date)}, ${elt.accepted.map(_.by)}, ${elt.rejected.map(_.date)}, ${elt.rejected.map(_.by)}, ${elt.canceled.map(_.date)}, ${elt.canceled.map(_.by)}"
      buildInsert(tableFr, fieldsFr, values).update
    }

    private[sql] def accept(id: UserRequest.Id, by: User.Id, now: Instant): doobie.Update0 = buildUpdate(tableFr, fr0"accepted=$now, accepted_by=$by", wherePending(id, now)).update

    private[sql] def reject(id: UserRequest.Id, by: User.Id, now: Instant): doobie.Update0 = buildUpdate(tableFr, fr0"rejected=$now, rejected_by=$by", wherePending(id, now)).update

    private[sql] def cancel(id: UserRequest.Id, by: User.Id, now: Instant): doobie.Update0 = buildUpdate(tableFr, fr0"canceled=$now, canceled_by=$by", wherePending(id, now)).update

    private[sql] def selectOnePending(group: Group.Id, id: UserRequest.Id): doobie.Query0[UserAskToJoinAGroupRequest] =
      buildSelect(tableFr, fieldsFrSelect, fr"WHERE id=$id AND kind=$kind AND group_id=$group AND accepted IS NULL AND rejected IS NULL AND canceled IS NULL").query[UserAskToJoinAGroupRequest]

    private[sql] def selectAllPending(user: User.Id): doobie.Query0[UserAskToJoinAGroupRequest] =
      buildSelect(tableFr, fieldsFrSelect, fr"WHERE kind=$kind AND created_by=$user AND accepted IS NULL AND rejected IS NULL AND canceled IS NULL").query[UserAskToJoinAGroupRequest]

    private def wherePending(id: UserRequest.Id, now: Instant): Fragment = fr0"WHERE id=$id AND kind=$kind AND accepted IS NULL AND rejected IS NULL AND canceled IS NULL"
  }

  object GroupInviteQueries {
    private val kind = "GroupInvite"
    private val fields = Seq("id", "kind", "group_id", "email", "created", "created_by", "accepted", "accepted_by", "rejected", "rejected_by", "canceled", "canceled_by")
    private val fieldsSelect = fields.filter(_ != "kind")
    private val fieldsFr: Fragment = Fragment.const0(fields.mkString(", "))
    private val fieldsFrSelect: Fragment = Fragment.const0(fieldsSelect.mkString(", "))

    private[sql] def insert(elt: GroupInvite): doobie.Update0 = {
      val values = fr0"${elt.id}, $kind, ${elt.group}, ${elt.email}, ${elt.created}, ${elt.createdBy}, ${elt.accepted.map(_.date)}, ${elt.accepted.map(_.by)}, ${elt.rejected.map(_.date)}, ${elt.rejected.map(_.by)}, ${elt.canceled.map(_.date)}, ${elt.canceled.map(_.by)}"
      buildInsert(tableFr, fieldsFr, values).update
    }

    private[sql] def accept(id: UserRequest.Id, by: User.Id, now: Instant): doobie.Update0 = buildUpdate(tableFr, fr0"accepted=$now, accepted_by=$by", wherePending(id, now)).update

    private[sql] def reject(id: UserRequest.Id, by: User.Id, now: Instant): doobie.Update0 = buildUpdate(tableFr, fr0"rejected=$now, rejected_by=$by", wherePending(id, now)).update

    private[sql] def cancel(id: UserRequest.Id, by: User.Id, now: Instant): doobie.Update0 = buildUpdate(tableFr, fr0"canceled=$now, canceled_by=$by", wherePending(id, now)).update

    private[sql] def selectOne(id: UserRequest.Id): doobie.Query0[GroupInvite] = buildSelect(tableFr, fieldsFrSelect, fr"WHERE id=$id").query[GroupInvite]

    private[sql] def selectAllPending(group: Group.Id): doobie.Query0[GroupInvite] =
      buildSelect(tableFr, fieldsFrSelect, fr"WHERE kind=$kind AND group_id=$group AND accepted IS NULL AND rejected IS NULL AND canceled IS NULL").query[GroupInvite]

    private def wherePending(id: UserRequest.Id, now: Instant): Fragment = fr0"WHERE id=$id AND kind=$kind AND accepted IS NULL AND rejected IS NULL AND canceled IS NULL"
  }

  object TalkInviteQueries {
    private val kind = "TalkInvite"
    private val fields = Seq("id", "kind", "talk_id", "email", "created", "created_by", "accepted", "accepted_by", "rejected", "rejected_by", "canceled", "canceled_by")
    private val fieldsSelect = fields.filter(_ != "kind")
    private val fieldsFr: Fragment = Fragment.const0(fields.mkString(", "))
    private val fieldsFrSelect: Fragment = Fragment.const0(fieldsSelect.mkString(", "))

    private[sql] def insert(elt: TalkInvite): doobie.Update0 = {
      val values = fr0"${elt.id}, $kind, ${elt.talk}, ${elt.email}, ${elt.created}, ${elt.createdBy}, ${elt.accepted.map(_.date)}, ${elt.accepted.map(_.by)}, ${elt.rejected.map(_.date)}, ${elt.rejected.map(_.by)}, ${elt.canceled.map(_.date)}, ${elt.canceled.map(_.by)}"
      buildInsert(tableFr, fieldsFr, values).update
    }

    private[sql] def accept(id: UserRequest.Id, by: User.Id, now: Instant): doobie.Update0 = buildUpdate(tableFr, fr0"accepted=$now, accepted_by=$by", wherePending(id, now)).update

    private[sql] def reject(id: UserRequest.Id, by: User.Id, now: Instant): doobie.Update0 = buildUpdate(tableFr, fr0"rejected=$now, rejected_by=$by", wherePending(id, now)).update

    private[sql] def cancel(id: UserRequest.Id, by: User.Id, now: Instant): doobie.Update0 = buildUpdate(tableFr, fr0"canceled=$now, canceled_by=$by", wherePending(id, now)).update

    private[sql] def selectOne(id: UserRequest.Id): doobie.Query0[TalkInvite] = buildSelect(tableFr, fieldsFrSelect, fr"WHERE id=$id").query[TalkInvite]

    private[sql] def selectAllPending(talk: Talk.Id): doobie.Query0[TalkInvite] =
      buildSelect(tableFr, fieldsFrSelect, fr"WHERE kind=$kind AND talk_id=$talk AND accepted IS NULL AND rejected IS NULL AND canceled IS NULL").query[TalkInvite]

    private def wherePending(id: UserRequest.Id, now: Instant): Fragment = fr0"WHERE id=$id AND kind=$kind AND accepted IS NULL AND rejected IS NULL AND canceled IS NULL"
  }

  object ProposalInviteQueries {
    private val kind = "ProposalInvite"
    private val fields = Seq("id", "kind", "proposal_id", "email", "created", "created_by", "accepted", "accepted_by", "rejected", "rejected_by", "canceled", "canceled_by")
    private val fieldsSelect = fields.filter(_ != "kind")
    private val fieldsFr: Fragment = Fragment.const0(fields.mkString(", "))
    private val fieldsFrSelect: Fragment = Fragment.const0(fieldsSelect.mkString(", "))

    private[sql] def insert(elt: ProposalInvite): doobie.Update0 = {
      val values = fr0"${elt.id}, $kind, ${elt.proposal}, ${elt.email}, ${elt.created}, ${elt.createdBy}, ${elt.accepted.map(_.date)}, ${elt.accepted.map(_.by)}, ${elt.rejected.map(_.date)}, ${elt.rejected.map(_.by)}, ${elt.canceled.map(_.date)}, ${elt.canceled.map(_.by)}"
      buildInsert(tableFr, fieldsFr, values).update
    }

    private[sql] def accept(id: UserRequest.Id, by: User.Id, now: Instant): doobie.Update0 = buildUpdate(tableFr, fr0"accepted=$now, accepted_by=$by", wherePending(id, now)).update

    private[sql] def reject(id: UserRequest.Id, by: User.Id, now: Instant): doobie.Update0 = buildUpdate(tableFr, fr0"rejected=$now, rejected_by=$by", wherePending(id, now)).update

    private[sql] def cancel(id: UserRequest.Id, by: User.Id, now: Instant): doobie.Update0 = buildUpdate(tableFr, fr0"canceled=$now, canceled_by=$by", wherePending(id, now)).update

    private[sql] def selectOne(id: UserRequest.Id): doobie.Query0[ProposalInvite] = buildSelect(tableFr, fieldsFrSelect, fr"WHERE id=$id").query[ProposalInvite]

    private[sql] def selectAllPending(proposal: Proposal.Id): doobie.Query0[ProposalInvite] =
      buildSelect(tableFr, fieldsFrSelect, fr"WHERE kind=$kind AND proposal_id=$proposal AND accepted IS NULL AND rejected IS NULL AND canceled IS NULL").query[ProposalInvite]

    private def wherePending(id: UserRequest.Id, now: Instant): Fragment = fr0"WHERE id=$id AND kind=$kind AND accepted IS NULL AND rejected IS NULL AND canceled IS NULL"
  }

}
