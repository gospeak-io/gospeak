package fr.gospeak.web.services.openapi.models.utils

import fr.gospeak.web.services.openapi.error.OpenApiError.ErrorMessage

final case class Url(value: String) extends AnyVal

object Url {
  private val regex = "(https?://.+)".r // useful to catch errors, not checking it's really valid

  def from(in: String): Either[List[ErrorMessage], Url] = in match {
    case regex(url) => Right(Url(url))
    case _ => Left(List(regexDoesNotMatch(in)))
  }

  private def regexDoesNotMatch(in: String): ErrorMessage =
    ErrorMessage.validationFailed(in, "https://...", "Url")
}
