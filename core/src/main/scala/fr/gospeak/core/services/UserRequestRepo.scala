package fr.gospeak.core.services

import java.time.Instant

import cats.effect.IO
import fr.gospeak.core.domain.UserRequest.{AccountValidationRequest, PasswordResetRequest}
import fr.gospeak.core.domain.{User, UserRequest}
import fr.gospeak.libs.scalautils.domain.{Done, EmailAddress}

trait UserRequestRepo {
  def createAccountValidationRequest(email: EmailAddress, user: User.Id, now: Instant): IO[AccountValidationRequest]

  def findPendingAccountValidationRequest(id: UserRequest.Id, now: Instant): IO[Option[AccountValidationRequest]]

  def findPendingAccountValidationRequest(id: User.Id, now: Instant): IO[Option[AccountValidationRequest]]

  def validateAccount(id: UserRequest.Id, user: User.Id, now: Instant): IO[Done]


  def createPasswordResetRequest(email: EmailAddress, now: Instant): IO[PasswordResetRequest]

  def findPendingPasswordResetRequest(id: UserRequest.Id, now: Instant): IO[Option[PasswordResetRequest]]

  def findPendingPasswordResetRequest(email: EmailAddress, now: Instant): IO[Option[PasswordResetRequest]]

  def resetPassword(passwordReset: PasswordResetRequest, credentials: User.Credentials, now: Instant): IO[Done]
}
