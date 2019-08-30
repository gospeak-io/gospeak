package fr.gospeak.infra.services.storage.sql

import java.time.Instant

import cats.effect.IO
import doobie.implicits._
import doobie.util.fragment.Fragment
import fr.gospeak.core.domain
import fr.gospeak.core.domain.UserRequest.{AccountValidationRequest, PasswordResetRequest, Timeout, UserAskToJoinAGroupRequest}
import fr.gospeak.core.domain.{Group, User, UserRequest}
import fr.gospeak.core.services.storage.UserRequestRepo
import fr.gospeak.infra.services.storage.sql.UserRequestRepoSql._
import fr.gospeak.infra.services.storage.sql.utils.GenericRepo
import fr.gospeak.infra.utils.DoobieUtils.Fragments._
import fr.gospeak.infra.utils.DoobieUtils.Mappings._
import fr.gospeak.infra.utils.DoobieUtils.Queries
import fr.gospeak.libs.scalautils.Extensions._
import fr.gospeak.libs.scalautils.domain.{Done, EmailAddress, Page}

class UserRequestRepoSql(protected[sql] val xa: doobie.Transactor[IO]) extends GenericRepo with UserRequestRepo {
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
    run(UserAskToJoinAGroup.insert, UserAskToJoinAGroupRequest(UserRequest.Id.generate(), group, now, user, None, None))

  override def acceptUserToJoinAGroup(req: UserAskToJoinAGroupRequest, by: User.Id, now: Instant): IO[Done] = for {
    _ <- run(UserAskToJoinAGroup.accept(req.id, by, now))
    _ <- run(GroupRepoSql.addOwner(req.group)(req.createdBy, by, now))
  } yield Done

  override def rejectUserToJoinAGroup(req: UserAskToJoinAGroupRequest, by: User.Id, now: Instant): IO[Done] =
    run(UserAskToJoinAGroup.reject(req.id, by, now))

  override def listPendingUserToJoinAGroupRequests(user: User.Id): IO[Seq[UserAskToJoinAGroupRequest]] =
    run(UserAskToJoinAGroup.selectAllPending(user).to[List])
}

object UserRequestRepoSql {
  private val _ = userRequestIdMeta // for intellij not remove DoobieUtils.Mappings import
  private val table = "requests"
  private val fields = Seq("id", "kind", "email", "group_id", "deadline", "created", "created_by", "accepted", "accepted_by", "rejected", "rejected_by")
  private val tableFr: Fragment = Fragment.const0(table)
  private val fieldsFr: Fragment = Fragment.const0(fields.mkString(", "))
  private val searchFields = Seq("id", "email", "group_id", "created_by")
  private val defaultSort = Page.OrderBy("-created")

  private[sql] def selectOnePending(group: Group.Id, req: domain.UserRequest.Id, now: Instant): doobie.Query0[UserRequest] =
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
    private val fields = Seq("id", "kind", "group_id", "created", "created_by", "accepted", "accepted_by", "rejected", "rejected_by")
    private val fieldsFr: Fragment = Fragment.const0(fields.mkString(", "))
    private val fieldsFrSelect: Fragment = Fragment.const0(fields.filter(_ != "kind").mkString(", "))

    private[sql] def insert(elt: UserAskToJoinAGroupRequest): doobie.Update0 = {
      val values = fr0"${elt.id}, $kind, ${elt.group}, ${elt.created}, ${elt.createdBy}, ${elt.accepted.map(_.date)}, ${elt.accepted.map(_.by)}, ${elt.rejected.map(_.date)}, ${elt.rejected.map(_.by)}"
      buildInsert(tableFr, fieldsFr, values).update
    }

    private[sql] def accept(id: UserRequest.Id, by: User.Id, now: Instant): doobie.Update0 =
      buildUpdate(tableFr, fr0"accepted=$now, accepted_by=$by", wherePending(id, now)).update

    private[sql] def reject(id: UserRequest.Id, by: User.Id, now: Instant): doobie.Update0 =
      buildUpdate(tableFr, fr0"rejected=$now, rejected_by=$by", wherePending(id, now)).update

    private[sql] def selectOnePending(group: Group.Id, id: UserRequest.Id): doobie.Query0[UserAskToJoinAGroupRequest] =
      buildSelect(tableFr, fieldsFrSelect, fr"WHERE kind=$kind AND id=$id AND group_id=$group AND accepted IS NULL AND rejected IS NULL").query[UserAskToJoinAGroupRequest]

    private[sql] def selectAllPending(user: User.Id): doobie.Query0[UserAskToJoinAGroupRequest] =
      buildSelect(tableFr, fieldsFrSelect, fr"WHERE kind=$kind AND created_by=$user AND accepted IS NULL AND rejected IS NULL").query[UserAskToJoinAGroupRequest]

    private def wherePending(id: UserRequest.Id, now: Instant): Fragment = fr0"WHERE id=$id AND kind=$kind AND accepted IS NULL AND rejected IS NULL"
  }

}
