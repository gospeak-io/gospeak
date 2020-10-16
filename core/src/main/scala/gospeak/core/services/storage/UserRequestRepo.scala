package gospeak.core.services.storage

import java.time.Instant

import cats.effect.IO
import gospeak.core.domain.UserRequest._
import gospeak.core.domain._
import gospeak.core.domain.utils.{OrgaCtx, UserAwareCtx, UserCtx}
import gospeak.libs.scala.domain.EmailAddress

trait UserRequestRepo extends OrgaUserRequestRepo with SpeakerUserRequestRepo with UserUserRequestRepo with AuthUserRequestRepo

trait OrgaUserRequestRepo {
  def listPendingGroupRequests(implicit ctx: OrgaCtx): IO[List[UserRequest]]


  def listPendingUserToJoinAGroupRequests(implicit ctx: UserCtx): IO[List[UserAskToJoinAGroupRequest]]

  def findPendingUserToJoinAGroup(req: UserRequest.Id)(implicit ctx: OrgaCtx): IO[Option[UserAskToJoinAGroupRequest]]

  def createUserAskToJoinAGroup(group: Group.Id)(implicit ctx: UserCtx): IO[UserAskToJoinAGroupRequest]

  def acceptUserToJoinAGroup(req: UserAskToJoinAGroupRequest)(implicit ctx: OrgaCtx): IO[Unit]

  def rejectUserToJoinAGroup(req: UserAskToJoinAGroupRequest)(implicit ctx: OrgaCtx): IO[Unit]


  def invite(email: EmailAddress)(implicit ctx: OrgaCtx): IO[GroupInvite]

  def cancelGroupInvite(id: UserRequest.Id)(implicit ctx: OrgaCtx): IO[GroupInvite]

  def listPendingInvites(implicit ctx: OrgaCtx): IO[List[GroupInvite]]


  def invite(proposal: Proposal.Id, email: EmailAddress)(implicit ctx: OrgaCtx): IO[ProposalInvite]

  def cancelProposalInvite(id: UserRequest.Id)(implicit ctx: OrgaCtx): IO[ProposalInvite]

  def listPendingInvites(proposal: Proposal.Id): IO[List[ProposalInvite]]
}

trait SpeakerUserRequestRepo {
  def invite(talk: Talk.Id, email: EmailAddress)(implicit ctx: UserCtx): IO[TalkInvite]

  def cancelTalkInvite(id: UserRequest.Id)(implicit ctx: UserCtx): IO[TalkInvite]

  def listPendingInvites(talk: Talk.Id): IO[List[TalkInvite]]


  def invite(proposal: Proposal.Id, email: EmailAddress)(implicit ctx: UserCtx): IO[ProposalInvite]

  def cancelProposalInvite(id: UserRequest.Id)(implicit ctx: UserCtx): IO[ProposalInvite]

  def listPendingInvites(proposal: Proposal.Id): IO[List[ProposalInvite]]


  def invite(proposal: ExternalProposal.Id, email: EmailAddress)(implicit ctx: UserCtx): IO[ExternalProposalInvite]

  def cancelExternalProposalInvite(id: UserRequest.Id)(implicit ctx: UserCtx): IO[ExternalProposalInvite]

  def listPendingInvites(proposal: ExternalProposal.Id): IO[List[ExternalProposalInvite]]
}

trait UserUserRequestRepo {
  def find(request: UserRequest.Id): IO[Option[UserRequest]]


  def accept(invite: UserRequest.GroupInvite)(implicit ctx: UserCtx): IO[Unit]

  def reject(invite: UserRequest.GroupInvite)(implicit ctx: UserCtx): IO[Unit]


  def accept(invite: UserRequest.TalkInvite)(implicit ctx: UserCtx): IO[Unit]

  def reject(invite: UserRequest.TalkInvite)(implicit ctx: UserCtx): IO[Unit]


  def accept(invite: UserRequest.ProposalInvite)(implicit ctx: UserCtx): IO[Unit]

  def reject(invite: UserRequest.ProposalInvite)(implicit ctx: UserCtx): IO[Unit]


  def accept(invite: UserRequest.ExternalProposalInvite)(implicit ctx: UserCtx): IO[Unit]

  def reject(invite: UserRequest.ExternalProposalInvite)(implicit ctx: UserCtx): IO[Unit]
}

trait AuthUserRequestRepo {
  def createAccountValidationRequest(email: EmailAddress, user: User.Id, now: Instant): IO[AccountValidationRequest]

  def validateAccount(id: UserRequest.Id, email: EmailAddress, now: Instant): IO[Unit]

  def findAccountValidationRequest(id: UserRequest.Id): IO[Option[AccountValidationRequest]]

  def findPendingAccountValidationRequest(id: User.Id, now: Instant): IO[Option[AccountValidationRequest]]


  def createPasswordResetRequest(email: EmailAddress, now: Instant): IO[PasswordResetRequest]

  def resetPassword(req: PasswordResetRequest, credentials: User.Credentials, user: User)(implicit ctx: UserAwareCtx): IO[Unit]

  def findPendingPasswordResetRequest(id: UserRequest.Id)(implicit ctx: UserAwareCtx): IO[Option[PasswordResetRequest]]

  def findPendingPasswordResetRequest(email: EmailAddress, now: Instant): IO[Option[PasswordResetRequest]]
}
