package fr.gospeak.core.services

import java.time.Instant

import cats.effect.IO
import fr.gospeak.core.domain.UserRequest.{AccountValidationRequest, PasswordResetRequest}
import fr.gospeak.core.domain.{User, UserRequest}
import fr.gospeak.libs.scalautils.domain.{Done, EmailAddress}

trait UserRequestRepo extends OrgaUserRequestRepo with SpeakerUserRequestRepo with UserUserRequestRepo with AuthUserRequestRepo

trait OrgaUserRequestRepo

trait SpeakerUserRequestRepo

trait UserUserRequestRepo

trait AuthUserRequestRepo {
  def createAccountValidationRequest(email: EmailAddress, user: User.Id, now: Instant): IO[AccountValidationRequest]

  def validateAccount(id: UserRequest.Id, email: EmailAddress, now: Instant): IO[Done]

  def findPendingAccountValidationRequest(id: UserRequest.Id, now: Instant): IO[Option[AccountValidationRequest]]

  def findPendingAccountValidationRequest(id: User.Id, now: Instant): IO[Option[AccountValidationRequest]]


  def createPasswordResetRequest(email: EmailAddress, now: Instant): IO[PasswordResetRequest]

  def resetPassword(passwordReset: PasswordResetRequest, credentials: User.Credentials, now: Instant): IO[Done]

  def findPendingPasswordResetRequest(id: UserRequest.Id, now: Instant): IO[Option[PasswordResetRequest]]

  def findPendingPasswordResetRequest(email: EmailAddress, now: Instant): IO[Option[PasswordResetRequest]]
}
