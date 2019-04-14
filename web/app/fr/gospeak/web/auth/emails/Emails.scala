package fr.gospeak.web.auth.emails

import com.mohiva.play.silhouette.api.actions.{SecuredRequest, UserAwareRequest}
import fr.gospeak.core.domain.User
import fr.gospeak.core.domain.UserRequest.{AccountValidationRequest, PasswordResetRequest}
import fr.gospeak.infra.services.EmailSrv.{Contact, Email, HtmlContent}
import fr.gospeak.libs.scalautils.domain.EmailAddress
import fr.gospeak.web.auth.domain.{AuthUser, CookieEnv}
import fr.gospeak.web.auth.emails
import play.api.i18n.Messages
import play.api.mvc.AnyContent

object Emails {
  val sender = Contact(EmailAddress.from("noreply@gospeak.fr").right.get, Some("Gospeak"))

  def signup(user: AuthUser, emailValidation: AccountValidationRequest)(implicit req: UserAwareRequest[CookieEnv, AnyContent], messages: Messages): Email = {
    Email(
      from = sender,
      to = Seq(Contact(user.user.email, Some(user.user.name.value))),
      subject = "Welcome to gospeak!",
      content = HtmlContent(emails.html.signup(user, emailValidation).body)
    )
  }

  def accountValidation(user: AuthUser, accountValidation: AccountValidationRequest)(implicit req: SecuredRequest[CookieEnv, AnyContent], messages: Messages): Email = {
    Email(
      from = sender,
      to = Seq(Contact(user.user.email, Some(user.user.name.value))),
      subject = "Validate your email!",
      content = HtmlContent(emails.html.accountValidation(user, accountValidation).body)
    )
  }

  def forgotPassword(user: User, passwordReset: PasswordResetRequest)(implicit req: UserAwareRequest[CookieEnv, AnyContent], messages: Messages): Email = {
    Email(
      from = sender,
      to = Seq(Contact(passwordReset.email, None)),
      subject = "Reset your password!",
      content = HtmlContent(emails.html.forgotPassword(user, passwordReset).body)
    )
  }
}
