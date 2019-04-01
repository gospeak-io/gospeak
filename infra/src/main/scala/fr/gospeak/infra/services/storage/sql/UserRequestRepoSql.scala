package fr.gospeak.infra.services.storage.sql

import java.time.Instant

import cats.effect.IO
import fr.gospeak.core.domain.UserRequest.{AccountValidationRequest, PasswordResetRequest}
import fr.gospeak.core.domain.{User, UserRequest}
import fr.gospeak.core.services.UserRequestRepo
import fr.gospeak.infra.services.storage.sql.tables.{UserRequestTable, UserTable}
import fr.gospeak.infra.services.storage.sql.utils.GenericRepo
import fr.gospeak.libs.scalautils.domain.{Done, EmailAddress}

class UserRequestRepoSql(protected[sql] val xa: doobie.Transactor[IO]) extends GenericRepo with UserRequestRepo {
  override def createAccountValidationRequest(email: EmailAddress, user: User.Id, now: Instant): IO[AccountValidationRequest] =
    run(UserRequestTable.AccountValidation.insert, AccountValidationRequest(email, user, now))

  override def findPendingAccountValidationRequest(id: UserRequest.Id, now: Instant): IO[Option[AccountValidationRequest]] =
    run(UserRequestTable.AccountValidation.selectPending(id, now).option)

  override def findPendingAccountValidationRequest(id: User.Id, now: Instant): IO[Option[AccountValidationRequest]] =
    run(UserRequestTable.AccountValidation.selectPending(id, now).option)

  override def validateAccount(id: UserRequest.Id, user: User.Id, now: Instant): IO[Done] = for {
    _ <- run(UserRequestTable.AccountValidation.accept(id, now))
    _ <- run(UserTable.validateAccount(user, now))
  } yield Done

  override def createPasswordResetRequest(email: EmailAddress, now: Instant): IO[PasswordResetRequest] =
    run(UserRequestTable.ResetPassword.insert, PasswordResetRequest(email, now))

  override def findPendingPasswordResetRequest(id: UserRequest.Id, now: Instant): IO[Option[PasswordResetRequest]] =
    run(UserRequestTable.ResetPassword.selectPending(id, now).option)

  override def findPendingPasswordResetRequest(email: EmailAddress, now: Instant): IO[Option[PasswordResetRequest]] =
    run(UserRequestTable.ResetPassword.selectPending(email, now).option)

  override def resetPassword(passwordReset: PasswordResetRequest, credentials: User.Credentials, now: Instant): IO[Done] = for {
    _ <- run(UserRequestTable.ResetPassword.accept(passwordReset.id, now))
    _ <- run(UserTable.updateCredentials(credentials.login)(credentials.pass))
  } yield Done
}
