package fr.gospeak.core.services.storage

import java.time.Instant

import cats.effect.IO
import fr.gospeak.core.domain.UserRequest._
import fr.gospeak.core.domain.utils.{OrgaCtx, UserCtx}
import fr.gospeak.core.domain._
import fr.gospeak.libs.scalautils.domain.{Done, EmailAddress, Page}

trait UserRequestRepo extends OrgaUserRequestRepo with SpeakerUserRequestRepo with UserUserRequestRepo with AuthUserRequestRepo

trait OrgaUserRequestRepo {
  def listPendingGroupRequests(implicit ctx: OrgaCtx): IO[Seq[UserRequest]]


  def listPendingUserToJoinAGroupRequests(implicit ctx: UserCtx): IO[Seq[UserAskToJoinAGroupRequest]]

  def findPendingUserToJoinAGroup(req: UserRequest.Id)(implicit ctx: OrgaCtx): IO[Option[UserAskToJoinAGroupRequest]]

  def createUserAskToJoinAGroup(group: Group.Id)(implicit ctx: UserCtx): IO[UserAskToJoinAGroupRequest]

  def acceptUserToJoinAGroup(req: UserAskToJoinAGroupRequest)(implicit ctx: OrgaCtx): IO[Done]

  def rejectUserToJoinAGroup(req: UserAskToJoinAGroupRequest)(implicit ctx: OrgaCtx): IO[Done]


  def invite(email: EmailAddress)(implicit ctx: OrgaCtx): IO[GroupInvite]

  def cancelGroupInvite(id: UserRequest.Id)(implicit ctx: OrgaCtx): IO[GroupInvite]

  def listPendingInvites(implicit ctx: OrgaCtx): IO[Seq[GroupInvite]]


  def invite(proposal: Proposal.Id, email: EmailAddress)(implicit ctx: OrgaCtx): IO[ProposalInvite]

  def cancelProposalInvite(id: UserRequest.Id)(implicit ctx: OrgaCtx): IO[ProposalInvite]

  def listPendingInvites(proposal: Proposal.Id): IO[Seq[ProposalInvite]]
}

trait SpeakerUserRequestRepo {
  def invite(talk: Talk.Id, email: EmailAddress)(implicit ctx: UserCtx): IO[TalkInvite]

  def cancelTalkInvite(id: UserRequest.Id)(implicit ctx: UserCtx): IO[TalkInvite]

  def listPendingInvites(talk: Talk.Id): IO[Seq[TalkInvite]]


  def invite(talk: Proposal.Id, email: EmailAddress)(implicit ctx: UserCtx): IO[ProposalInvite]

  def cancelProposalInvite(id: UserRequest.Id)(implicit ctx: UserCtx): IO[ProposalInvite]

  def listPendingInvites(proposal: Proposal.Id): IO[Seq[ProposalInvite]]
}

trait UserUserRequestRepo {
  def find(request: UserRequest.Id): IO[Option[UserRequest]]

  def list(user: User.Id, params: Page.Params): IO[Page[UserRequest]]


  def accept(invite: UserRequest.GroupInvite)(implicit ctx: UserCtx): IO[Done]

  def reject(invite: UserRequest.GroupInvite)(implicit ctx: UserCtx): IO[Done]


  def accept(invite: UserRequest.TalkInvite)(implicit ctx: UserCtx): IO[Done]

  def reject(invite: UserRequest.TalkInvite)(implicit ctx: UserCtx): IO[Done]


  def accept(invite: UserRequest.ProposalInvite)(implicit ctx: UserCtx): IO[Done]

  def reject(invite: UserRequest.ProposalInvite)(implicit ctx: UserCtx): IO[Done]
}

trait AuthUserRequestRepo {
  def createAccountValidationRequest(email: EmailAddress, user: User.Id, now: Instant): IO[AccountValidationRequest]

  def validateAccount(id: UserRequest.Id, email: EmailAddress, now: Instant): IO[Done]

  def findAccountValidationRequest(id: UserRequest.Id): IO[Option[AccountValidationRequest]]

  def findPendingAccountValidationRequest(id: User.Id, now: Instant): IO[Option[AccountValidationRequest]]


  def createPasswordResetRequest(email: EmailAddress, now: Instant): IO[PasswordResetRequest]

  def resetPassword(passwordReset: PasswordResetRequest, credentials: User.Credentials, now: Instant): IO[Done]

  def findPendingPasswordResetRequest(id: UserRequest.Id, now: Instant): IO[Option[PasswordResetRequest]]

  def findPendingPasswordResetRequest(email: EmailAddress, now: Instant): IO[Option[PasswordResetRequest]]
}
