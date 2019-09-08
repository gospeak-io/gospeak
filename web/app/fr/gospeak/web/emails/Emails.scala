package fr.gospeak.web.emails

import com.mohiva.play.silhouette.api.actions.{SecuredRequest, UserAwareRequest}
import fr.gospeak.core.domain.{Group, Talk, User, UserRequest}
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

  def inviteSpeakerToTalk(invite: UserRequest.TalkInvite, talk: Talk, by: User)(implicit req: SecuredRequest[CookieEnv, AnyContent], messages: Messages): Email =
    Email(
      from = sender,
      to = Seq(Contact(invite.email)),
      subject = s"You have been invited to join ${by.name.value} for a talk: ${talk.title.value}",
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
}
