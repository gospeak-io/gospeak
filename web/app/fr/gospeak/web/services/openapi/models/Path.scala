package fr.gospeak.web.services.openapi.models

import cats.data.NonEmptyList
import fr.gospeak.web.services.openapi.error.OpenApiError.ErrorMessage

/**
 * @see "https://spec.openapis.org/oas/v3.0.2#paths-object"
 */
final case class Path(value: String) extends AnyVal

object Path {
  def from(value: String): Either[NonEmptyList[ErrorMessage], Path] = {
    if (value.startsWith("/")) Right(new Path(value))
    else Left(NonEmptyList.of(ErrorMessage.badFormat(value, "Path", "/...")))
  }
}
