package fr.gospeak.core.services.storage

import java.time.Instant

import cats.effect.IO
import fr.gospeak.core.domain.UserRequest.{AccountValidationRequest, GroupInvite, PasswordResetRequest, ProposalInvite, TalkInvite, UserAskToJoinAGroupRequest}
import fr.gospeak.core.domain.{Group, Proposal, Talk, User, UserRequest}
import fr.gospeak.libs.scalautils.domain.{Done, EmailAddress, Page}

trait UserRequestRepo extends OrgaUserRequestRepo with SpeakerUserRequestRepo with UserUserRequestRepo with AuthUserRequestRepo

trait OrgaUserRequestRepo {
  def listPendingGroupRequests(group: Group.Id, now: Instant): IO[Seq[UserRequest]]


  def listPendingUserToJoinAGroupRequests(user: User.Id): IO[Seq[UserAskToJoinAGroupRequest]]

  def findPendingUserToJoinAGroup(group: Group.Id, req: UserRequest.Id): IO[Option[UserAskToJoinAGroupRequest]]

  def createUserAskToJoinAGroup(user: User.Id, group: Group.Id, now: Instant): IO[UserAskToJoinAGroupRequest]

  def acceptUserToJoinAGroup(req: UserAskToJoinAGroupRequest, by: User.Id, now: Instant): IO[Done]

  def rejectUserToJoinAGroup(req: UserAskToJoinAGroupRequest, by: User.Id, now: Instant): IO[Done]


  def invite(group: Group.Id, email: EmailAddress, by: User.Id, now: Instant): IO[GroupInvite]

  def cancelGroupInvite(id: UserRequest.Id, by: User.Id, now: Instant): IO[GroupInvite]

  def listPendingInvites(group: Group.Id): IO[Seq[GroupInvite]]


  def invite(talk: Proposal.Id, email: EmailAddress, by: User.Id, now: Instant): IO[ProposalInvite]

  def cancelProposalInvite(id: UserRequest.Id, by: User.Id, now: Instant): IO[ProposalInvite]

  def listPendingInvites(proposal: Proposal.Id): IO[Seq[ProposalInvite]]
}

trait SpeakerUserRequestRepo {
  def invite(talk: Talk.Id, email: EmailAddress, by: User.Id, now: Instant): IO[TalkInvite]

  def cancelTalkInvite(id: UserRequest.Id, by: User.Id, now: Instant): IO[TalkInvite]

  def listPendingInvites(talk: Talk.Id): IO[Seq[TalkInvite]]


  def invite(talk: Proposal.Id, email: EmailAddress, by: User.Id, now: Instant): IO[ProposalInvite]

  def cancelProposalInvite(id: UserRequest.Id, by: User.Id, now: Instant): IO[ProposalInvite]

  def listPendingInvites(proposal: Proposal.Id): IO[Seq[ProposalInvite]]
}

trait UserUserRequestRepo {
  def find(request: UserRequest.Id): IO[Option[UserRequest]]

  def list(user: User.Id, params: Page.Params): IO[Page[UserRequest]]


  def accept(invite: UserRequest.GroupInvite, by: User.Id, now: Instant): IO[Done]

  def reject(invite: UserRequest.GroupInvite, by: User.Id, now: Instant): IO[Done]


  def accept(invite: UserRequest.TalkInvite, by: User.Id, now: Instant): IO[Done]

  def reject(invite: UserRequest.TalkInvite, by: User.Id, now: Instant): IO[Done]


  def accept(invite: UserRequest.ProposalInvite, by: User.Id, now: Instant): IO[Done]

  def reject(invite: UserRequest.ProposalInvite, by: User.Id, now: Instant): IO[Done]
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
