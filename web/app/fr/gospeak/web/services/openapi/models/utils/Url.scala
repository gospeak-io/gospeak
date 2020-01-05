package fr.gospeak.web.services.openapi.models.utils

import cats.data.NonEmptyList
import fr.gospeak.web.services.openapi.error.OpenApiError.ErrorMessage

final case class Url(value: String) extends AnyVal

object Url {
  private val regex = "(https?://.+)".r // useful to catch errors, not checking it's really valid

  def from(value: String): Either[NonEmptyList[ErrorMessage], Url] = value match {
    case regex(url) => Right(Url(url))
    case _ => Left(NonEmptyList.of(ErrorMessage.badFormat(value, "Url", "https://...")))
  }
}
