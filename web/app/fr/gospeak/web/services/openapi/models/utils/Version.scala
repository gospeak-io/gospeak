package fr.gospeak.web.services.openapi.models.utils

import cats.data.NonEmptyList
import fr.gospeak.web.services.openapi.error.OpenApiError.ErrorMessage

final case class Version(major: Int, minor: Int, patch: Int) {
  def format: String = s"$major.$minor.$patch"
}

object Version {
  private val regex = "([0-9]+)(\\.[0-9]+)?(\\.[0-9]+)?".r

  def apply(major: Int, minor: Int): Version = new Version(major, minor, 0)

  def apply(major: Int): Version = new Version(major, 0, 0)

  def from(value: String): Either[NonEmptyList[ErrorMessage], Version] = value match {
    case regex(majorStr, minorStr, patchStr) =>
      Right(Version(
        major = majorStr.toInt, // safe, thanks to regex
        minor = Option(minorStr).getOrElse(".0").drop(1).toInt,
        patch = Option(patchStr).getOrElse(".0").drop(1).toInt))
    case _ => Left(NonEmptyList.of(ErrorMessage.badFormat(value, "Version", "1.2.3")))
  }
}
