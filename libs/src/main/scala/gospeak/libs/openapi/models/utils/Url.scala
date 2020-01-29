package gospeak.libs.openapi.models.utils

import gospeak.libs.openapi.error.OpenApiError

final case class Url(value: String) extends AnyVal

object Url {
  private val regex = "(https?://.+)".r // useful to catch errors, not checking it's really valid

  def from(value: String): Either[OpenApiError, Url] = value match {
    case regex(url) => Right(Url(url))
    case _ => Left(OpenApiError.badFormat(value, "Url", "https://..."))
  }
}
