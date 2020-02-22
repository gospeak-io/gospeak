package gospeak.web.emails

import cats.data.NonEmptyList
import gospeak.core.domain.UserRequest.{AccountValidationRequest, PasswordResetRequest}
import gospeak.core.domain._
import gospeak.core.domain.utils.Constants
import gospeak.core.services.email.EmailSrv.{Email, HtmlContent}
import gospeak.libs.scala.domain.{EmailAddress, Markdown}
import gospeak.web.utils.{OrgaReq, UserAwareReq, UserReq}
import play.api.mvc.AnyContent

object Emails {

  implicit class UserExtension(val user: User) extends AnyVal {
    def asContact: EmailAddress.Contact = EmailAddress.Contact(user.email, Some(user.name.value))
  }

  def signup(emailValidation: AccountValidationRequest, user: User)(implicit req: UserAwareReq[AnyContent]): Email =
    Email(
      from = Constants.Contact.noReply,
      to = Seq(user.asContact),
      subject = "Welcome to gospeak!",
      content = HtmlContent(html.signup(emailValidation, user).body))

  def accountValidation(accountValidation: AccountValidationRequest, user: User)(implicit req: UserAwareReq[AnyContent]): Email =
    Email(
      from = Constants.Contact.noReply,
      to = Seq(user.asContact),
      subject = "Validate your email!",
      content = HtmlContent(html.accountValidation(accountValidation, user).body))

  def forgotPassword(user: User, passwordReset: PasswordResetRequest)(implicit req: UserAwareReq[AnyContent]): Email =
    Email(
      from = Constants.Contact.noReply,
      to = Seq(EmailAddress.Contact(passwordReset.email)),
      subject = "Reset your password!",
      content = HtmlContent(html.forgotPassword(user, passwordReset).body))

  def joinGroupAccepted(acceptedUser: User)(implicit req: OrgaReq[AnyContent]): Email =
    Email(
      from = Constants.Contact.noReply,
      to = Seq(acceptedUser.asContact),
      subject = s"Welcome to ${req.group.name.value} group!",
      content = HtmlContent(html.joinGroupAccepted(acceptedUser).body))

  def joinGroupRejected(rejectedUser: User)(implicit req: OrgaReq[AnyContent]): Email =
    Email(
      from = Constants.Contact.noReply,
      to = Seq(rejectedUser.asContact),
      subject = s"Your application to ${req.group.name.value} group was rejected :(",
      content = HtmlContent(html.joinGroupRejected(rejectedUser).body))

  def inviteOrgaToGroup(invite: UserRequest.GroupInvite, message: Markdown)(implicit req: OrgaReq[AnyContent]): Email =
    Email(
      from = Constants.Contact.noReply.withName(req.user.name.value),
      to = Seq(EmailAddress.Contact(invite.email)),
      subject = s"Hi, join me in ${req.group.name.value} orga",
      content = HtmlContent(html.inviteOrgaToGroup(invite, message).body))

  def inviteOrgaToGroupCanceled(invite: UserRequest.GroupInvite)(implicit req: OrgaReq[AnyContent]): Email =
    Email(
      from = Constants.Contact.noReply,
      to = Seq(EmailAddress.Contact(invite.email)),
      subject = s"Your invitation for the group ${req.group.name.value} has been canceled :(",
      content = HtmlContent(html.inviteOrgaToGroupCanceled(invite).body))

  def inviteOrgaToGroupAccepted(invite: UserRequest.GroupInvite, group: Group, orga: User)(implicit req: UserReq[AnyContent]): Email =
    Email(
      from = Constants.Contact.noReply,
      to = Seq(EmailAddress.Contact(group.contact.getOrElse(orga.email))),
      subject = s"${req.user.name.value} has accepted your invitation for the ${group.name.value} group",
      content = HtmlContent(html.inviteOrgaToGroupAccepted(invite, group, orga).body))

  def inviteOrgaToGroupRejected(invite: UserRequest.GroupInvite, group: Group, orga: User)(implicit req: UserReq[AnyContent]): Email =
    Email(
      from = Constants.Contact.noReply,
      to = Seq(EmailAddress.Contact(group.contact.getOrElse(orga.email))),
      subject = s"Oups, ${req.user.name.value} rejected your invitation in the ${group.name.value} group :(",
      content = HtmlContent(html.inviteOrgaToGroupRejected(invite, group, orga).body))

  def orgaRemovedFromGroup(orga: User)(implicit req: OrgaReq[AnyContent]): Email =
    Email(
      from = Constants.Contact.noReply,
      to = Seq(orga.asContact),
      subject = s"${req.user.name.value} removed you from the organizers of ${req.group.name.value} :(",
      content = HtmlContent(html.orgaRemovedFromGroup(orga).body))

  def inviteSpeakerToTalk(invite: UserRequest.TalkInvite, talk: Talk, message: Markdown)(implicit req: UserReq[AnyContent]): Email =
    Email(
      from = Constants.Contact.noReply.withName(req.user.name.value),
      to = Seq(EmailAddress.Contact(invite.email)),
      subject = s"Hi, let's make '${talk.title.value}' together",
      content = HtmlContent(html.inviteSpeakerToTalk(invite, talk, message).body))

  def inviteSpeakerToTalkCanceled(invite: UserRequest.TalkInvite, talk: Talk)(implicit req: UserReq[AnyContent]): Email =
    Email(
      from = Constants.Contact.noReply,
      to = Seq(EmailAddress.Contact(invite.email)),
      subject = s"Your invitation for the talk '${talk.title.value}' has been canceled :(",
      content = HtmlContent(html.inviteSpeakerToTalkCanceled(invite, talk).body))

  def inviteSpeakerToTalkAccepted(invite: UserRequest.TalkInvite, talk: Talk, speaker: User)(implicit req: UserReq[AnyContent]): Email =
    Email(
      from = Constants.Contact.noReply,
      to = Seq(speaker.asContact),
      subject = s"${req.user.name.value} has accepted your invitation for the talk '${talk.title.value}'",
      content = HtmlContent(html.inviteSpeakerToTalkAccepted(invite, talk, speaker).body))

  def inviteSpeakerToTalkRejected(invite: UserRequest.TalkInvite, talk: Talk, speaker: User)(implicit req: UserReq[AnyContent]): Email =
    Email(
      from = Constants.Contact.noReply,
      to = Seq(speaker.asContact),
      subject = s"Oups, ${req.user.name.value} rejected your invitation for the talk '${talk.title.value}' :(",
      content = HtmlContent(html.inviteSpeakerToTalkRejected(invite, talk, speaker).body))

  def speakerRemovedFromTalk(talk: Talk, speaker: User)(implicit req: UserReq[AnyContent]): Email =
    Email(
      from = Constants.Contact.noReply,
      to = Seq(speaker.asContact),
      subject = s"${req.user.name.value} removed you from the speakers of '${talk.title.value}' :(",
      content = HtmlContent(html.speakerRemovedFromTalk(talk, speaker).body))

  def inviteSpeakerToProposal(invite: UserRequest.ProposalInvite, cfp: Cfp, event: Option[Event], proposal: Proposal, message: Markdown)(implicit req: UserReq[AnyContent]): Email =
    Email(
      from = Constants.Contact.noReply.withName(req.user.name.value),
      to = Seq(EmailAddress.Contact(invite.email)),
      subject = s"Hi, let's make '${proposal.title.value}' together at ${event.map(_.name.value).getOrElse(cfp.name.value)}",
      content = HtmlContent(html.inviteSpeakerToProposal(invite, cfp, event, proposal, message).body))

  def inviteSpeakerToProposalCanceled(invite: UserRequest.ProposalInvite, proposal: Proposal)(implicit req: UserReq[AnyContent]): Email =
    Email(
      from = Constants.Contact.noReply,
      to = Seq(EmailAddress.Contact(invite.email)),
      subject = s"Your invitation for speaking about '${proposal.title.value}' has been canceled :(",
      content = HtmlContent(html.inviteSpeakerToProposalCanceled(invite, proposal).body))

  def inviteSpeakerToProposalAccepted(invite: UserRequest.ProposalInvite, proposal: Proposal.Full, speaker: User)(implicit req: UserReq[AnyContent]): Email =
    Email(
      from = Constants.Contact.noReply,
      to = Seq(speaker.asContact),
      subject = s"${req.user.name.value} has accepted your invitation to speak about '${proposal.title.value}'",
      content = HtmlContent(html.inviteSpeakerToProposalAccepted(invite, proposal, speaker).body))

  def inviteSpeakerToProposalRejected(invite: UserRequest.ProposalInvite, proposal: Proposal.Full, speaker: User)(implicit req: UserReq[AnyContent]): Email =
    Email(
      from = Constants.Contact.noReply,
      to = Seq(speaker.asContact),
      subject = s"Oups, ${req.user.name.value} rejected your invitation to speak about '${proposal.title.value}' :(",
      content = HtmlContent(html.inviteSpeakerToProposalRejected(invite, proposal, speaker).body))

  def speakerRemovedFromProposal(proposal: Proposal, speaker: User)(implicit req: UserReq[AnyContent]): Email =
    Email(
      from = Constants.Contact.noReply,
      to = Seq(speaker.asContact),
      subject = s"${req.user.name.value} removed you from the speakers of '${proposal.title.value}' proposal :(",
      content = HtmlContent(html.speakerRemovedFromProposal(proposal, speaker).body))

  def inviteSpeakerToExtProposal(invite: UserRequest.ExternalProposalInvite, proposal: ExternalProposal.Full, message: Markdown)(implicit req: UserReq[AnyContent]): Email =
    Email(
      from = Constants.Contact.noReply.withName(req.user.name.value),
      to = Seq(EmailAddress.Contact(invite.email)),
      subject = s"Add you to '${proposal.title.value}' at ${proposal.event.name.value}",
      content = HtmlContent(html.inviteSpeakerToExtProposal(invite, proposal, message).body))

  def inviteSpeakerToExtProposalCanceled(invite: UserRequest.ExternalProposalInvite, proposal: ExternalProposal.Full)(implicit req: UserReq[AnyContent]): Email =
    Email(
      from = Constants.Contact.noReply,
      to = Seq(EmailAddress.Contact(invite.email)),
      subject = s"Your invitation for '${proposal.title.value}' has been canceled :(",
      content = HtmlContent(html.inviteSpeakerToExtProposalCanceled(invite, proposal).body))

  def inviteSpeakerToExtProposalAccepted(invite: UserRequest.ExternalProposalInvite, proposal: ExternalProposal.Full, speaker: User)(implicit req: UserReq[AnyContent]): Email =
    Email(
      from = Constants.Contact.noReply,
      to = Seq(speaker.asContact),
      subject = s"${req.user.name.value} has accepted your invitation for '${proposal.title.value}'",
      content = HtmlContent(html.inviteSpeakerToExtProposalAccepted(invite, proposal, speaker).body))

  def inviteSpeakerToExtProposalRejected(invite: UserRequest.ExternalProposalInvite, proposal: ExternalProposal.Full, speaker: User)(implicit req: UserReq[AnyContent]): Email =
    Email(
      from = Constants.Contact.noReply,
      to = Seq(speaker.asContact),
      subject = s"Oups, ${req.user.name.value} rejected your invitation for '${proposal.title.value}' :(",
      content = HtmlContent(html.inviteSpeakerToExtProposalRejected(invite, proposal, speaker).body))

  def speakerRemovedFromExtProposal(proposal: ExternalProposal.Full, speaker: User)(implicit req: UserReq[AnyContent]): Email =
    Email(
      from = Constants.Contact.noReply,
      to = Seq(speaker.asContact),
      subject = s"${req.user.name.value} removed you from the speakers of '${proposal.title.value}' at ${proposal.event.name.value} :(",
      content = HtmlContent(html.speakerRemovedFromExtProposal(proposal, speaker).body))

  def movedFromWaitingListToAttendees(group: Group, event: Event, attendee: User)(implicit req: UserReq[AnyContent]): Email =
    Email(
      from = Constants.Contact.noReply,
      to = Seq(attendee.asContact),
      subject = s"You are now on the ${event.name.value} guest list",
      content = HtmlContent(html.movedFromWaitingListToAttendees(group, event, attendee).body))

  def eventPublished(event: Event, venueOpt: Option[Venue.Full], member: Group.Member)(implicit req: OrgaReq[AnyContent]): Email =
    Email(
      from = Constants.Contact.noReply.withName(req.group.name.value),
      to = Seq(member.user.asContact),
      subject = s"New upcoming event: ${event.name.value}",
      content = HtmlContent(html.eventPublished(event, venueOpt, member).body))

  def proposalCommentAddedForSpeaker(cfp: Cfp, talk: Talk, proposal: Proposal, speakers: NonEmptyList[User], comment: Comment)(implicit req: OrgaReq[AnyContent]): Email =
    Email(
      from = Constants.Contact.noReply.withName(req.user.name.value),
      to = speakers.map(_.asContact).toList,
      subject = s"New comment on your '${proposal.title.value}' proposal for ${cfp.name.value}",
      content = HtmlContent(html.proposalCommentAddedForSpeaker(cfp, talk, proposal, speakers, comment).body))

  def proposalCommentAddedForOrga(group: Group, cfp: Cfp, proposal: Proposal, orgas: NonEmptyList[User], comment: Comment)(implicit req: UserReq[AnyContent]): Email =
    Email(
      from = Constants.Contact.noReply.withName(req.user.name.value),
      to = orgas.map(_.asContact).toList,
      subject = s"New comment about '${proposal.title.value}' proposal on ${cfp.name.value} CFP",
      content = HtmlContent(html.proposalCommentAddedForOrga(group, cfp, proposal, orgas, comment).body))

  def eventCommentAdded(group: Group, event: Event, orga: User, comment: Comment)(implicit req: UserReq[AnyContent]): Email =
    Email(
      from = Constants.Contact.noReply,
      to = Seq(orga.asContact),
      subject = s"New comment on event '${event.name.value}'",
      content = HtmlContent(html.eventCommentAdded(group, event, orga, comment).body))

  def groupMessage(group: Group, sender: EmailAddress.Contact, subject: String, text: Markdown, member: Group.Member)(implicit req: UserReq[AnyContent]): Email =
    Email(
      from = sender,
      to = Seq(member.user.asContact),
      subject = subject,
      content = HtmlContent(html.groupMessage(group, text, member).body))

  def eventMessage(event: Event, sender: EmailAddress.Contact, subject: String, text: Markdown, rsvp: Event.Rsvp)(implicit req: OrgaReq[AnyContent]): Email =
    Email(
      from = sender,
      to = Seq(rsvp.user.asContact),
      subject = subject,
      content = HtmlContent(html.eventMessage(event, text, rsvp).body))


  def contactSpeaker(sender: EmailAddress.Contact, subject: String, text: Markdown, speaker: User): Email =
    Email(
      from = sender,
      to = Seq(speaker.asContact),
      subject = subject,
      content = HtmlContent(html.contactSpeaker(text).body))
}
