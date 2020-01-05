package fr.gospeak.web.services.openapi.models.utils

import cats.data.NonEmptyList
import fr.gospeak.web.services.openapi.error.OpenApiError.ErrorMessage

final case class Email(value: String) extends AnyVal

object Email {
  private val regex = "(.+@.+\\..+)".r // useful to catch errors, not checking it's really valid

  def from(value: String): Either[NonEmptyList[ErrorMessage], Email] = value match {
    case regex(email) => Right(Email(email))
    case _ => Left(NonEmptyList.of(ErrorMessage.badFormat(value, "Email", "example@mail.com")))
  }
}
