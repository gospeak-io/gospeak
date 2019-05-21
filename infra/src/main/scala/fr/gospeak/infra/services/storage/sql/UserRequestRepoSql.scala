package fr.gospeak.infra.services.storage.sql

import java.time.Instant

import cats.effect.IO
import doobie.implicits._
import doobie.util.fragment.Fragment
import fr.gospeak.core.domain.UserRequest.{AccountValidationRequest, PasswordResetRequest}
import fr.gospeak.core.domain.{User, UserRequest}
import fr.gospeak.core.services.storage.UserRequestRepo
import fr.gospeak.infra.services.storage.sql.UserRequestRepoSql._
import fr.gospeak.infra.services.storage.sql.utils.GenericRepo
import fr.gospeak.infra.utils.DoobieUtils.Fragments._
import fr.gospeak.infra.utils.DoobieUtils.Mappings._
import fr.gospeak.libs.scalautils.domain.{Done, EmailAddress}

class UserRequestRepoSql(protected[sql] val xa: doobie.Transactor[IO]) extends GenericRepo with UserRequestRepo {
  override def createAccountValidationRequest(email: EmailAddress, user: User.Id, now: Instant): IO[AccountValidationRequest] =
    run(AccountValidation.insert, AccountValidationRequest(email, user, now))

  override def validateAccount(id: UserRequest.Id, email: EmailAddress, now: Instant): IO[Done] = for {
    _ <- run(AccountValidation.accept(id, now))
    _ <- run(UserRepoSql.validateAccount(email, now))
  } yield Done

  override def findPendingAccountValidationRequest(id: UserRequest.Id, now: Instant): IO[Option[AccountValidationRequest]] =
    run(AccountValidation.selectPending(id, now).option)

  override def findPendingAccountValidationRequest(id: User.Id, now: Instant): IO[Option[AccountValidationRequest]] =
    run(AccountValidation.selectPending(id, now).option)


  override def createPasswordResetRequest(email: EmailAddress, now: Instant): IO[PasswordResetRequest] =
    run(ResetPassword.insert, PasswordResetRequest(email, now))

  override def resetPassword(passwordReset: PasswordResetRequest, credentials: User.Credentials, now: Instant): IO[Done] = for {
    _ <- run(ResetPassword.accept(passwordReset.id, now))
    _ <- run(UserRepoSql.updateCredentials(credentials.login)(credentials.pass))
    _ <- run(UserRepoSql.validateAccount(passwordReset.email, now))
  } yield Done

  override def findPendingPasswordResetRequest(id: UserRequest.Id, now: Instant): IO[Option[PasswordResetRequest]] =
    run(ResetPassword.selectPending(id, now).option)

  override def findPendingPasswordResetRequest(email: EmailAddress, now: Instant): IO[Option[PasswordResetRequest]] =
    run(ResetPassword.selectPending(email, now).option)
}

object UserRequestRepoSql {
  private val _ = userRequestIdMeta // for intellij not remove DoobieUtils.Mappings import
  private val table = "requests"
  private val tableFr: Fragment = Fragment.const0(table)

  object AccountValidation {
    private val kind = "AccountValidation"
    private val fields = Seq("id", "kind", "email", "user_id", "deadline", "created", "accepted")

    private[sql] def insert(elt: AccountValidationRequest): doobie.Update0 = {
      val values = fr0"${elt.id}, $kind, ${elt.email}, ${elt.user}, ${elt.deadline}, ${elt.created}, ${elt.accepted}"
      buildInsert(tableFr, Fragment.const0(fields.mkString(", ")), values).update
    }

    private[sql] def accept(id: UserRequest.Id, now: Instant): doobie.Update0 =
      buildUpdate(tableFr, fr0"accepted=$now", where(id, now)).update

    private[sql] def selectPending(id: UserRequest.Id, now: Instant): doobie.Query0[AccountValidationRequest] =
      buildSelect(tableFr, Fragment.const0(fields.filter(_ != "kind").mkString(", ")), where(id, now)).query[AccountValidationRequest]

    private[sql] def selectPending(id: User.Id, now: Instant): doobie.Query0[AccountValidationRequest] =
      buildSelect(tableFr, Fragment.const0(fields.filter(_ != "kind").mkString(", ")), where(id, now)).query[AccountValidationRequest]

    private def where(id: UserRequest.Id, now: Instant): Fragment = fr0"WHERE id=$id AND kind=$kind AND deadline > $now AND accepted IS NULL"

    private def where(id: User.Id, now: Instant): Fragment = fr0"WHERE user_id=$id AND kind=$kind AND deadline > $now AND accepted IS NULL"
  }

  object ResetPassword {
    private val kind = "ResetPassword"
    private val fields = Seq("id", "kind", "email", "deadline", "created", "accepted")

    private[sql] def insert(elt: PasswordResetRequest): doobie.Update0 = {
      val values = fr0"${elt.id}, $kind, ${elt.email}, ${elt.deadline}, ${elt.created}, ${elt.accepted}"
      buildInsert(tableFr, Fragment.const0(fields.mkString(", ")), values).update
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

}
