package fr.gospeak.core.services.storage

import java.time.Instant

import cats.effect.IO
import fr.gospeak.core.domain.UserRequest.{AccountValidationRequest, PasswordResetRequest, TalkInvite, UserAskToJoinAGroupRequest}
import fr.gospeak.core.domain.{Group, Talk, User, UserRequest}
import fr.gospeak.libs.scalautils.domain.{Done, EmailAddress, Page}

trait UserRequestRepo extends OrgaUserRequestRepo with SpeakerUserRequestRepo with UserUserRequestRepo with AuthUserRequestRepo

trait OrgaUserRequestRepo {
  def listPendingGroupRequests(group: Group.Id, now: Instant): IO[Seq[UserRequest]]

  def findPendingUserToJoinAGroup(group: Group.Id, req: UserRequest.Id): IO[Option[UserAskToJoinAGroupRequest]]

  def acceptUserToJoinAGroup(req: UserAskToJoinAGroupRequest, by: User.Id, now: Instant): IO[Done]

  def rejectUserToJoinAGroup(req: UserAskToJoinAGroupRequest, by: User.Id, now: Instant): IO[Done]
}

trait SpeakerUserRequestRepo {
  def invite(talk: Talk.Id, email: EmailAddress, by: User.Id, now: Instant): IO[TalkInvite]

  def cancelInvite(id: UserRequest.Id, by: User.Id, now: Instant): IO[TalkInvite]

  def listPendingTalkInvites(talk: Talk.Id): IO[Seq[TalkInvite]]
}

trait UserUserRequestRepo {
  def find(request: UserRequest.Id): IO[Option[UserRequest]]

  def list(user: User.Id, params: Page.Params): IO[Page[UserRequest]]


  def createUserAskToJoinAGroup(user: User.Id, group: Group.Id, now: Instant): IO[UserAskToJoinAGroupRequest]

  def listPendingUserToJoinAGroupRequests(user: User.Id): IO[Seq[UserAskToJoinAGroupRequest]]


  def accept(request: UserRequest.TalkInvite, by: User.Id, now: Instant): IO[Done]

  def reject(request: UserRequest.TalkInvite, by: User.Id, now: Instant): IO[Done]
}

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
