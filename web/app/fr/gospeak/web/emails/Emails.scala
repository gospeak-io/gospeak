package fr.gospeak.web.emails

import fr.gospeak.core.domain.UserRequest.{AccountValidationRequest, PasswordResetRequest}
import fr.gospeak.core.domain._
import fr.gospeak.core.domain.utils.Constants
import fr.gospeak.infra.services.EmailSrv.{Email, HtmlContent}
import fr.gospeak.libs.scalautils.domain.{EmailAddress, Markdown}
import fr.gospeak.web.utils.{SecuredReq, UserAwareReq}
import play.api.mvc.AnyContent

object Emails {

  implicit class UserExtension(val user: User) extends AnyVal {
    def asContact: EmailAddress.Contact = EmailAddress.Contact(user.email, Some(user.name.value))
  }

  def signup(emailValidation: AccountValidationRequest)(implicit req: SecuredReq[AnyContent]): Email =
    Email(
      from = Constants.Contact.noReply,
      to = Seq(req.user.asContact),
      subject = "Welcome to gospeak!",
      content = HtmlContent(html.signup(emailValidation).body))

  def accountValidation(accountValidation: AccountValidationRequest)(implicit req: SecuredReq[AnyContent]): Email =
    Email(
      from = Constants.Contact.noReply,
      to = Seq(req.user.asContact),
      subject = "Validate your email!",
      content = HtmlContent(html.accountValidation(accountValidation).body))

  def forgotPassword(user: User, passwordReset: PasswordResetRequest)(implicit req: UserAwareReq[AnyContent]): Email =
    Email(
      from = Constants.Contact.noReply,
      to = Seq(EmailAddress.Contact(passwordReset.email)),
      subject = "Reset your password!",
      content = HtmlContent(html.forgotPassword(user, passwordReset).body))

  def inviteOrgaToGroup(invite: UserRequest.GroupInvite, group: Group)(implicit req: SecuredReq[AnyContent]): Email =
    Email(
      from = Constants.Contact.noReply,
      to = Seq(EmailAddress.Contact(invite.email)),
      subject = s"You have been invited to join ${req.user.name.value} in the ${group.name.value} group",
      content = HtmlContent(html.inviteOrgaToGroup(invite, group).body))

  def inviteOrgaToGroupCanceled(invite: UserRequest.GroupInvite, group: Group)(implicit req: SecuredReq[AnyContent]): Email =
    Email(
      from = Constants.Contact.noReply,
      to = Seq(EmailAddress.Contact(invite.email)),
      subject = s"Your invitation for the group ${group.name.value} has been canceled :(",
      content = HtmlContent(html.inviteOrgaToGroupCanceled(invite, group).body))

  def inviteOrgaToGroupAccepted(invite: UserRequest.GroupInvite, group: Group, orga: User)(implicit req: SecuredReq[AnyContent]): Email =
    Email(
      from = Constants.Contact.noReply,
      to = Seq(EmailAddress.Contact(group.contact.getOrElse(orga.email))),
      subject = s"${req.user.name.value} has accepted your invitation for the ${group.name.value} group",
      content = HtmlContent(html.inviteOrgaToGroupAccepted(invite, group, orga).body))

  def inviteOrgaToGroupRejected(invite: UserRequest.GroupInvite, group: Group, orga: User)(implicit req: SecuredReq[AnyContent]): Email =
    Email(
      from = Constants.Contact.noReply,
      to = Seq(EmailAddress.Contact(group.contact.getOrElse(orga.email))),
      subject = s"Oups, ${req.user.name.value} rejected your invitation in the ${group.name.value} group :(",
      content = HtmlContent(html.inviteOrgaToGroupRejected(invite, group, orga).body))

  def joinGroupAccepted(acceptedUser: User, group: Group)(implicit req: SecuredReq[AnyContent]): Email =
    Email(
      from = Constants.Contact.noReply,
      to = Seq(acceptedUser.asContact),
      subject = s"Welcome to ${group.name.value} group!",
      content = HtmlContent(html.joinGroupAccepted(acceptedUser, group).body))

  def joinGroupRejected(rejectedUser: User, group: Group)(implicit req: SecuredReq[AnyContent]): Email =
    Email(
      from = Constants.Contact.noReply,
      to = Seq(rejectedUser.asContact),
      subject = s"Your application to ${group.name.value} group was rejected :(",
      content = HtmlContent(html.joinGroupRejected(rejectedUser, group).body))

  def orgaRemovedFromGroup(group: Group, orga: User)(implicit req: SecuredReq[AnyContent]): Email =
    Email(
      from = Constants.Contact.noReply,
      to = Seq(orga.asContact),
      subject = s"${req.user.name.value} removed you from the organizers of ${group.name.value} :(",
      content = HtmlContent(html.orgaRemovedFromGroup(group, orga).body))

  def inviteSpeakerToTalk(invite: UserRequest.TalkInvite, talk: Talk)(implicit req: SecuredReq[AnyContent]): Email =
    Email(
      from = Constants.Contact.noReply,
      to = Seq(EmailAddress.Contact(invite.email)),
      subject = s"You have been invited to join ${req.user.name.value} for a talk '${talk.title.value}'",
      content = HtmlContent(html.inviteSpeakerToTalk(invite, talk).body))

  def inviteSpeakerToTalkCanceled(invite: UserRequest.TalkInvite, talk: Talk)(implicit req: SecuredReq[AnyContent]): Email =
    Email(
      from = Constants.Contact.noReply,
      to = Seq(EmailAddress.Contact(invite.email)),
      subject = s"Your invitation for the talk '${talk.title.value}' has been canceled :(",
      content = HtmlContent(html.inviteSpeakerToTalkCanceled(invite, talk).body))

  def inviteSpeakerToTalkAccepted(invite: UserRequest.TalkInvite, talk: Talk, speaker: User)(implicit req: SecuredReq[AnyContent]): Email =
    Email(
      from = Constants.Contact.noReply,
      to = Seq(speaker.asContact),
      subject = s"${req.user.name.value} has accepted your invitation for the talk '${talk.title.value}'",
      content = HtmlContent(html.inviteSpeakerToTalkAccepted(invite, talk, speaker).body))

  def inviteSpeakerToTalkRejected(invite: UserRequest.TalkInvite, talk: Talk, speaker: User)(implicit req: SecuredReq[AnyContent]): Email =
    Email(
      from = Constants.Contact.noReply,
      to = Seq(speaker.asContact),
      subject = s"Oups, ${req.user.name.value} rejected your invitation for the talk '${talk.title.value}' :(",
      content = HtmlContent(html.inviteSpeakerToTalkRejected(invite, talk, speaker).body))

  def speakerRemovedFromTalk(talk: Talk, speaker: User)(implicit req: SecuredReq[AnyContent]): Email =
    Email(
      from = Constants.Contact.noReply,
      to = Seq(speaker.asContact),
      subject = s"${req.user.name.value} removed you from the speakers of '${talk.title.value}' :(",
      content = HtmlContent(html.speakerRemovedFromTalk(talk, speaker).body))

  def inviteSpeakerToProposal(invite: UserRequest.ProposalInvite, proposal: Proposal)(implicit req: SecuredReq[AnyContent]): Email =
    Email(
      from = Constants.Contact.noReply,
      to = Seq(EmailAddress.Contact(invite.email)),
      subject = s"You have been invited to join ${req.user.name.value} to speak about '${proposal.title.value}'",
      content = HtmlContent(html.inviteSpeakerToProposal(invite, proposal).body))

  def inviteSpeakerToProposalCanceled(invite: UserRequest.ProposalInvite, proposal: Proposal)(implicit req: SecuredReq[AnyContent]): Email =
    Email(
      from = Constants.Contact.noReply,
      to = Seq(EmailAddress.Contact(invite.email)),
      subject = s"Your invitation for speaking about '${proposal.title.value}' has been canceled :(",
      content = HtmlContent(html.inviteSpeakerToProposalCanceled(invite, proposal).body))

  def inviteSpeakerToProposalAccepted(invite: UserRequest.ProposalInvite, proposal: Proposal.Full, speaker: User)(implicit req: SecuredReq[AnyContent]): Email =
    Email(
      from = Constants.Contact.noReply,
      to = Seq(speaker.asContact),
      subject = s"${req.user.name.value} has accepted your invitation to speak about '${proposal.title.value}'",
      content = HtmlContent(html.inviteSpeakerToProposalAccepted(invite, proposal, speaker).body))

  def inviteSpeakerToProposalRejected(invite: UserRequest.ProposalInvite, proposal: Proposal.Full, speaker: User)(implicit req: SecuredReq[AnyContent]): Email =
    Email(
      from = Constants.Contact.noReply,
      to = Seq(speaker.asContact),
      subject = s"Oups, ${req.user.name.value} rejected your invitation to speak about '${proposal.title.value}' :(",
      content = HtmlContent(html.inviteSpeakerToProposalRejected(invite, proposal, speaker).body))

  def speakerRemovedFromProposal(proposal: Proposal, speaker: User)(implicit req: SecuredReq[AnyContent]): Email =
    Email(
      from = Constants.Contact.noReply,
      to = Seq(speaker.asContact),
      subject = s"${req.user.name.value} removed you from the speakers of '${proposal.title.value}' proposal :(",
      content = HtmlContent(html.speakerRemovedFromProposal(proposal, speaker).body))

  def movedFromWaitingListToAttendees(group: Group, event: Event, attendee: User)(implicit req: SecuredReq[AnyContent]): Email =
    Email(
      from = Constants.Contact.noReply,
      to = Seq(attendee.asContact),
      subject = s"You are now on the ${event.name.value} guest list",
      content = HtmlContent(html.movedFromWaitingListToAttendees(group, event, attendee).body))

  def proposalCreationRequested(group: Group, cfp: Cfp, event: Option[Event], r: UserRequest.ProposalCreation)(implicit req: SecuredReq[AnyContent]): Email =
    Email(
      from = Constants.Contact.noReply,
      to = Seq(EmailAddress.Contact(r.email, Some(r.payload.account.name))),
      subject = s"Speaking proposal at ${event.map(_.name.value).getOrElse(cfp.name.value)}",
      content = HtmlContent(html.proposalCreationRequested(group, cfp, event, r).body))

  def proposalCreationCanceled(group: Group, cfp: Cfp, event: Option[Event], r: UserRequest.ProposalCreation)(implicit req: SecuredReq[AnyContent]): Email =
    Email(
      from = Constants.Contact.noReply,
      to = Seq(EmailAddress.Contact(r.email, Some(r.payload.account.name))),
      subject = s"Your speaking proposal at ${event.map(_.name.value).getOrElse(cfp.name.value)} has been canceled",
      content = HtmlContent(html.proposalCreationCanceled(group, cfp, event, r).body))

  def proposalCreationAccepted(group: Group, cfp: Cfp, event: Option[Event], r: UserRequest.ProposalCreation, orga: User)(implicit req: SecuredReq[AnyContent]): Email =
    Email(
      from = Constants.Contact.noReply,
      to = Seq(group.contact.map(EmailAddress.Contact(_)).getOrElse(orga.asContact)),
      subject = s"${req.user.name.value} has accepted to speak about ${r.payload.submission.title.value} at ${event.map(_.name.value).getOrElse(cfp.name.value)}",
      content = HtmlContent(html.proposalCreationAccepted(group, cfp, event, r).body))

  def proposalCreationRejected(group: Group, cfp: Cfp, event: Option[Event], r: UserRequest.ProposalCreation, orga: User)(implicit req: UserAwareReq[AnyContent]): Email =
    Email(
      from = Constants.Contact.noReply,
      to = Seq(EmailAddress.Contact(r.email, Some(r.payload.account.name))),
      subject = s"${req.user.map(_.name.value).getOrElse("Anonymous")} has rejected to speak about ${r.payload.submission.title.value} at ${event.map(_.name.value).getOrElse(cfp.name.value)}",
      content = HtmlContent(html.proposalCreationRejected(group, cfp, event, r).body))

  def eventPublished(group: Group, event: Event, venueOpt: Option[Venue.Full], member: Group.Member)(implicit req: SecuredReq[AnyContent]): Email =
    Email(
      from = Constants.Contact.noReply,
      to = Seq(member.user.asContact),
      subject = s"New event from ${group.name.value}: ${event.name.value}",
      content = HtmlContent(html.eventPublished(group, event, venueOpt, member).body))

  def groupMessage(group: Group, sender: EmailAddress.Contact, subject: String, text: Markdown, member: Group.Member)(implicit req: SecuredReq[AnyContent]): Email =
    Email(
      from = sender,
      to = Seq(member.user.asContact),
      subject = subject,
      content = HtmlContent(html.groupMessage(group, text, member).body))

  def eventMessage(group: Group, event: Event, sender: EmailAddress.Contact, subject: String, text: Markdown, rsvp: Event.Rsvp)(implicit req: SecuredReq[AnyContent]): Email =
    Email(
      from = sender,
      to = Seq(rsvp.user.asContact),
      subject = subject,
      content = HtmlContent(html.eventMessage(group, event, text, rsvp).body))
}
