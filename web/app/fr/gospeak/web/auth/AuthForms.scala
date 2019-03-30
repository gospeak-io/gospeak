package fr.gospeak.web.auth

import fr.gospeak.core.domain.User
import fr.gospeak.libs.scalautils.domain.{Email, Secret}
import fr.gospeak.web.utils.Mappings._
import play.api.data.Form
import play.api.data.Forms._

object AuthForms {

  final case class SignupData(slug: User.Slug, firstName: String, lastName: String, email: Email, password: Secret, rememberMe: Boolean)

  val signup: Form[SignupData] = Form(mapping(
    "slug" -> userSlug,
    "first-name" -> text(1, 30),
    "last-name" -> text(1, 30),
    "email" -> mail,
    "password" -> secret,
    "rememberMe" -> boolean
  )(SignupData.apply)(SignupData.unapply))

  final case class LoginData(email: Email, password: Secret, rememberMe: Boolean)

  val login: Form[LoginData] = Form(mapping(
    "email" -> mail,
    "password" -> secret,
    "rememberMe" -> boolean
  )(LoginData.apply)(LoginData.unapply))

  final case class ForgotPasswordData(email: Email)

  val forgotPassword: Form[ForgotPasswordData] = Form(mapping(
    "email" -> mail
  )(ForgotPasswordData.apply)(ForgotPasswordData.unapply))

  final case class ResetPasswordData(password: Secret, rememberMe: Boolean)

  val resetPassword: Form[ResetPasswordData] = Form(mapping(
    "password" -> secret,
    "rememberMe" -> boolean
  )(ResetPasswordData.apply)(ResetPasswordData.unapply))
}
