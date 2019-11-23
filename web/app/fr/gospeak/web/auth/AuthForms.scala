package fr.gospeak.web.auth

import fr.gospeak.core.domain.User
import fr.gospeak.core.domain.utils.SocialAccounts
import fr.gospeak.libs.scalautils.domain.{Avatar, EmailAddress, Secret, Values}
import fr.gospeak.web.utils.Mappings._
import play.api.data.Forms._
import play.api.data.{Form, Mapping}

object AuthForms {

  final case class SignupData(slug: User.Slug, firstName: String, lastName: String, email: EmailAddress, password: Secret, rememberMe: Boolean) {
    def data(avatar: Avatar): User.Data = User.Data(slug, User.Status.Undefined, firstName, lastName, email, avatar, None, None, None, None, None, SocialAccounts.fromUrls())
  }

  val signupMapping: Mapping[SignupData] = mapping(
    "slug" -> userSlug,
    "first-name" -> text(1, Values.maxLength.title),
    "last-name" -> text(1, Values.maxLength.title),
    "email" -> emailAddress,
    "password" -> password,
    "rememberMe" -> boolean
  )(SignupData.apply)(SignupData.unapply)
  val signup: Form[SignupData] = Form(signupMapping)

  final case class LoginData(email: EmailAddress, password: Secret, rememberMe: Boolean)

  val loginMapping: Mapping[LoginData] = mapping(
    "email" -> emailAddress,
    "password" -> secret,
    "rememberMe" -> boolean
  )(LoginData.apply)(LoginData.unapply)
  val login: Form[LoginData] = Form(loginMapping)

  final case class ForgotPasswordData(email: EmailAddress)

  val forgotPassword: Form[ForgotPasswordData] = Form(mapping(
    "email" -> emailAddress
  )(ForgotPasswordData.apply)(ForgotPasswordData.unapply))

  final case class ResetPasswordData(password: Secret, rememberMe: Boolean)

  val resetPassword: Form[ResetPasswordData] = Form(mapping(
    "password" -> password,
    "rememberMe" -> boolean
  )(ResetPasswordData.apply)(ResetPasswordData.unapply))
}
