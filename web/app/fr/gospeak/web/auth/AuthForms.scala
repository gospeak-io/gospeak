package fr.gospeak.web.auth

import fr.gospeak.core.domain.User
import fr.gospeak.libs.scalautils.domain.{Email, Secret}
import fr.gospeak.web.utils.Mappings._
import play.api.data.Form
import play.api.data.Forms._

object AuthForms {

  final case class SignupData(slug: User.Slug, firstName: String, lastName: String, email: Email, password: Secret)

  val signup: Form[SignupData] = Form(mapping(
    "slug" -> userSlug,
    "first-name" -> text(1, 30),
    "last-name" -> text(1, 30),
    "email" -> mail,
    "password" -> secret
  )(SignupData.apply)(SignupData.unapply))

  final case class LoginData(email: Email, password: Secret)

  val login: Form[LoginData] = Form(mapping(
    "email" -> mail,
    "password" -> secret
  )(LoginData.apply)(LoginData.unapply))

  final case class PasswordResetData(email: Email)

  val passwordReset: Form[PasswordResetData] = Form(mapping(
    "email" -> mail
  )(PasswordResetData.apply)(PasswordResetData.unapply))
}
