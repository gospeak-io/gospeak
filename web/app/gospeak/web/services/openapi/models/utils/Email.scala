package gospeak.web.services.openapi.models.utils

import gospeak.web.services.openapi.error.OpenApiError

final case class Email(value: String) extends AnyVal

object Email {
  private val regex = "(.+@.+\\..+)".r // useful to catch errors, not checking it's really valid

  def from(value: String): Either[OpenApiError, Email] = value match {
    case regex(email) => Right(Email(email))
    case _ => Left(OpenApiError.badFormat(value, "Email", "example@mail.com"))
  }
}
