package gospeak.libs.openapi.models.utils

import gospeak.libs.openapi.error.OpenApiError

final case class Version(major: Int, minor: Int, patch: Int) {
  def format: String = s"$major.$minor.$patch"
}

object Version {
  private val regex = "([0-9]+)(\\.[0-9]+)?(\\.[0-9]+)?".r

  def apply(major: Int, minor: Int): Version = new Version(major, minor, 0)

  def apply(major: Int): Version = new Version(major, 0, 0)

  def from(value: String): Either[OpenApiError, Version] = value match {
    case regex(majorStr, minorStr, patchStr) =>
      Right(Version(
        major = majorStr.toInt, // safe, thanks to regex
        minor = Option(minorStr).getOrElse(".0").drop(1).toInt,
        patch = Option(patchStr).getOrElse(".0").drop(1).toInt))
    case _ => Left(OpenApiError.badFormat(value, "Version", "1.2.3"))
  }
}
