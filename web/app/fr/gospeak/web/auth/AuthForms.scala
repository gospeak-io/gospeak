package fr.gospeak.web.auth

import fr.gospeak.core.domain.utils.{Email, Password}
import fr.gospeak.web.utils.Mappings._
import play.api.data.Form
import play.api.data.Forms._

object AuthForms {

  case class Signup(firstName: String, lastName: String, email: Email, password: Password)

  val signup: Form[Signup] = Form(mapping(
    "first-name" -> nonEmptyText,
    "last-name" -> nonEmptyText,
    "email" -> mail,
    "password" -> password
  )(Signup.apply)(Signup.unapply))

  case class Login(email: Email, password: Password)

  val login: Form[Login] = Form(mapping(
    "email" -> mail,
    "password" -> password
  )(Login.apply)(Login.unapply))

  case class PasswordReset(email: Email)

  val passwordReset: Form[PasswordReset] = Form(mapping(
    "email" -> mail
  )(PasswordReset.apply)(PasswordReset.unapply))
}
