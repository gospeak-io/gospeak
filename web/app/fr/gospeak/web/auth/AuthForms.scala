package fr.gospeak.web.auth

import fr.gospeak.core.domain.User
import fr.gospeak.libs.scalautils.domain.{Email, Secret}
import fr.gospeak.web.utils.Mappings._
import play.api.data.Form
import play.api.data.Forms._

object AuthForms {

  final case class Signup(slug: User.Slug, firstName: String, lastName: String, email: Email, password: Secret)

  val signup: Form[Signup] = Form(mapping(
    "slug" -> userSlug,
    "first-name" -> nonEmptyText,
    "last-name" -> nonEmptyText,
    "email" -> mail,
    "password" -> secret
  )(Signup.apply)(Signup.unapply))

  final case class Login(email: Email, password: Secret)

  val login: Form[Login] = Form(mapping(
    "email" -> mail,
    "password" -> secret
  )(Login.apply)(Login.unapply))

  final case class PasswordReset(email: Email)

  val passwordReset: Form[PasswordReset] = Form(mapping(
    "email" -> mail
  )(PasswordReset.apply)(PasswordReset.unapply))
}
