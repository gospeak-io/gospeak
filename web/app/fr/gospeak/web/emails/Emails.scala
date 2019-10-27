package fr.gospeak.web.emails

import com.mohiva.play.silhouette.api.actions.{SecuredRequest, UserAwareRequest}
import fr.gospeak.core.domain.{Cfp, Group, Proposal, Talk, User, UserRequest}
import fr.gospeak.core.domain.UserRequest.{AccountValidationRequest, PasswordResetRequest}
import fr.gospeak.infra.services.EmailSrv.{Contact, Email, HtmlContent}
import fr.gospeak.libs.scalautils.domain.EmailAddress
import fr.gospeak.web.auth.domain.{AuthUser, CookieEnv}
import play.api.i18n.Messages
import play.api.mvc.AnyContent
;

object Emails {
  private val sender = Contact(EmailAddress.from("noreply@gospeak.fr").right.get, Some("Gospeak"))

  def signup(user: AuthUser, emailValidation: AccountValidationRequest)(implicit req: UserAwareRequest[CookieEnv, AnyContent], messages: Messages): Email =
    Email(
      from = sender,
      to = Seq(Contact(user.user)),
      subject = "Welcome to gospeak!",
      content = HtmlContent(html.signup(user, emailValidation).body))

  def accountValidation(user: AuthUser, accountValidation: AccountValidationRequest)(implicit req: SecuredRequest[CookieEnv, AnyContent], messages: Messages): Email =
    Email(
      from = sender,
      to = Seq(Contact(user.user)),
      subject = "Validate your email!",
      content = HtmlContent(html.accountValidation(user, accountValidation).body))

  def forgotPassword(user: User, passwordReset: PasswordResetRequest)(implicit req: UserAwareRequest[CookieEnv, AnyContent], messages: Messages): Email =
    Email(
      from = sender,
      to = Seq(Contact(passwordReset.email, None)),
      subject = "Reset your password!",
      content = HtmlContent(html.forgotPassword(user, passwordReset).body))

  def joinGroupAccepted(acceptedUser: User, acceptingOrga: AuthUser, group: Group)(implicit req: SecuredRequest[CookieEnv, AnyContent], messages: Messages): Email =
    Email(
      from = sender,
      to = Seq(Contact(acceptedUser)),
      subject = s"Welcome to ${group.name.value} group!",
      content = HtmlContent(html.joinGroupAccepted(acceptedUser, acceptingOrga, group).body))

  def joinGroupRejected(rejectedUser: User, acceptingOrga: AuthUser, group: Group)(implicit req: SecuredRequest[CookieEnv, AnyContent], messages: Messages): Email =
    Email(
      from = sender,
      to = Seq(Contact(rejectedUser)),
      subject = s"Your application to ${group.name.value} group was rejected :(",
      content = HtmlContent(html.joinGroupRejected(rejectedUser, acceptingOrga, group).body))

  def inviteOrgaToGroup(invite: UserRequest.GroupInvite, group: Group, by: User)(implicit req: SecuredRequest[CookieEnv, AnyContent], messages: Messages): Email =
    Email(
      from = sender,
      to = Seq(Contact(invite.email)),
      subject = s"You have been invited to join ${by.name.value} in the ${group.name.value} group",
      content = HtmlContent(html.inviteOrgaToGroup(invite, group, by).body))

  def inviteOrgaToGroupCanceled(invite: UserRequest.GroupInvite, group: Group, by: User)(implicit req: SecuredRequest[CookieEnv, AnyContent], messages: Messages): Email =
    Email(
      from = sender,
      to = Seq(Contact(invite.email)),
      subject = s"Your invitation for the group ${group.name.value} has been canceled :(",
      content = HtmlContent(html.inviteOrgaToGroupCanceled(invite, group, by).body))

  def inviteOrgaToGroupAccepted(invite: UserRequest.GroupInvite, group: Group, orga: User, by: User)(implicit req: SecuredRequest[CookieEnv, AnyContent], messages: Messages): Email =
    Email(
      from = sender,
      to = Seq(Contact(group.contact.getOrElse(orga.email))),
      subject = s"${by.name.value} has accepted your invitation for the ${group.name.value} group",
      content = HtmlContent(html.inviteOrgaToGroupAccepted(invite, group, orga, by).body))

  def inviteOrgaToGroupRejected(invite: UserRequest.GroupInvite, group: Group, orga: User, by: User)(implicit req: SecuredRequest[CookieEnv, AnyContent], messages: Messages): Email =
    Email(
      from = sender,
      to = Seq(Contact(group.contact.getOrElse(orga.email))),
      subject = s"Oups, ${by.name.value} rejected your invitation in the ${group.name.value} group :(",
      content = HtmlContent(html.inviteOrgaToGroupRejected(invite, group, orga, by).body))

  def orgaRemovedFromGroup(group: Group, orga: User, by: User)(implicit req: SecuredRequest[CookieEnv, AnyContent], messages: Messages): Email =
    Email(
      from = sender,
      to = Seq(Contact(orga)),
      subject = s"${by.name.value} removed you from the organizers of ${group.name.value} :(",
      content = HtmlContent(html.orgaRemovedFromGroup(group, orga, by).body))

  def inviteSpeakerToTalk(invite: UserRequest.TalkInvite, talk: Talk, by: User)(implicit req: SecuredRequest[CookieEnv, AnyContent], messages: Messages): Email =
    Email(
      from = sender,
      to = Seq(Contact(invite.email)),
      subject = s"You have been invited to join ${by.name.value} for a talk '${talk.title.value}'",
      content = HtmlContent(html.inviteSpeakerToTalk(invite, talk, by).body))

  def inviteSpeakerToTalkCanceled(invite: UserRequest.TalkInvite, talk: Talk, by: User)(implicit req: SecuredRequest[CookieEnv, AnyContent], messages: Messages): Email =
    Email(
      from = sender,
      to = Seq(Contact(invite.email)),
      subject = s"Your invitation for the talk '${talk.title.value}' has been canceled :(",
      content = HtmlContent(html.inviteSpeakerToTalkCanceled(invite, talk, by).body))

  def inviteSpeakerToTalkAccepted(invite: UserRequest.TalkInvite, talk: Talk, speaker: User, by: User)(implicit req: SecuredRequest[CookieEnv, AnyContent], messages: Messages): Email =
    Email(
      from = sender,
      to = Seq(Contact(speaker)),
      subject = s"${by.name.value} has accepted your invitation for the talk '${talk.title.value}'",
      content = HtmlContent(html.inviteSpeakerToTalkAccepted(invite, talk, speaker, by).body))

  def inviteSpeakerToTalkRejected(invite: UserRequest.TalkInvite, talk: Talk, speaker: User, by: User)(implicit req: SecuredRequest[CookieEnv, AnyContent], messages: Messages): Email =
    Email(
      from = sender,
      to = Seq(Contact(speaker)),
      subject = s"Oups, ${by.name.value} rejected your invitation for the talk '${talk.title.value}' :(",
      content = HtmlContent(html.inviteSpeakerToTalkRejected(invite, talk, speaker, by).body))

  def speakerRemovedFromTalk(talk: Talk, speaker: User, by: User)(implicit req: SecuredRequest[CookieEnv, AnyContent], messages: Messages): Email =
    Email(
      from = sender,
      to = Seq(Contact(speaker)),
      subject = s"${by.name.value} removed you from the speakers of '${talk.title.value}' :(",
      content = HtmlContent(html.speakerRemovedFromTalk(talk, speaker, by).body))

  def inviteSpeakerToProposal(invite: UserRequest.ProposalInvite, proposal: Proposal, by: User)(implicit req: SecuredRequest[CookieEnv, AnyContent], messages: Messages): Email =
    Email(
      from = sender,
      to = Seq(Contact(invite.email)),
      subject = s"You have been invited to join ${by.name.value} to speak about '${proposal.title.value}'",
      content = HtmlContent(html.inviteSpeakerToProposal(invite, proposal, by).body))

  def inviteSpeakerToProposalCanceled(invite: UserRequest.ProposalInvite, proposal: Proposal, by: User)(implicit req: SecuredRequest[CookieEnv, AnyContent], messages: Messages): Email =
    Email(
      from = sender,
      to = Seq(Contact(invite.email)),
      subject = s"Your invitation for speaking about '${proposal.title.value}' has been canceled :(",
      content = HtmlContent(html.inviteSpeakerToProposalCanceled(invite, proposal, by).body))

  def inviteSpeakerToProposalAccepted(invite: UserRequest.ProposalInvite, proposal: Proposal.Full, speaker: User, by: User)(implicit req: SecuredRequest[CookieEnv, AnyContent], messages: Messages): Email =
    Email(
      from = sender,
      to = Seq(Contact(speaker)),
      subject = s"${by.name.value} has accepted your invitation to speak about '${proposal.title.value}'",
      content = HtmlContent(html.inviteSpeakerToProposalAccepted(invite, proposal, speaker, by).body))

  def inviteSpeakerToProposalRejected(invite: UserRequest.ProposalInvite, proposal: Proposal.Full, speaker: User, by: User)(implicit req: SecuredRequest[CookieEnv, AnyContent], messages: Messages): Email =
    Email(
      from = sender,
      to = Seq(Contact(speaker)),
      subject = s"Oups, ${by.name.value} rejected your invitation to speak about '${proposal.title.value}' :(",
      content = HtmlContent(html.inviteSpeakerToProposalRejected(invite, proposal, speaker, by).body))

  def speakerRemovedFromProposal(proposal: Proposal, speaker: User, by: User)(implicit req: SecuredRequest[CookieEnv, AnyContent], messages: Messages): Email =
    Email(
      from = sender,
      to = Seq(Contact(speaker)),
      subject = s"${by.name.value} removed you from the speakers of '${proposal.title.value}' proposal :(",
      content = HtmlContent(html.speakerRemovedFromProposal(proposal, speaker, by).body))
}
