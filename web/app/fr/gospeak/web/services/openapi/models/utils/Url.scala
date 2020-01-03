package fr.gospeak.web.services.openapi.models.utils

import fr.gospeak.web.services.openapi.error.OpenApiError.Message

final case class Url(value: String) extends AnyVal

object Url {
  private val regex = "(https?://.+)".r // useful to catch errors, not checking it's really valid

  def from(in: String): Either[Seq[Message], Url] = in match {
    case regex(url) => Right(Url(url))
    case _ => Left(Seq(regexDoesNotMatch(in)))
  }

  private def regexDoesNotMatch(in: String): Message =
    Message.validationFailed(in, "https://...", "Url")
}
