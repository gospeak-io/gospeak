package fr.gospeak.web.services.openapi.models.utils

import fr.gospeak.web.services.openapi.error.OpenApiError.ErrorMessage

final case class Email(value: String) extends AnyVal

object Email {
  private val regex = "(.+@.+\\..+)".r // useful to catch errors, not checking it's really valid

  def from(in: String): Either[Seq[ErrorMessage], Email] = in match {
    case regex(email) => Right(Email(email))
    case _ => Left(Seq(regexDoesNotMatch(in)))
  }

  private def regexDoesNotMatch(in: String): ErrorMessage =
    ErrorMessage.validationFailed(in, "https://...", "Url")
}
