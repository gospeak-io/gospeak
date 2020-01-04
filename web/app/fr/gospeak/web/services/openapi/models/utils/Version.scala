package fr.gospeak.web.services.openapi.models.utils

import fr.gospeak.web.services.openapi.error.OpenApiError.ErrorMessage

final case class Version(major: Int, minor: Int, patch: Int) {
  def format: String = s"$major.$minor.$patch"
}

object Version {
  private val regex = "([0-9]+)(\\.[0-9]+)?(\\.[0-9]+)?".r

  def apply(major: Int, minor: Int): Version = new Version(major, minor, 0)

  def apply(major: Int): Version = new Version(major, 0, 0)

  def from(in: String): Either[List[ErrorMessage], Version] = in match {
    case regex(majorStr, minorStr, patchStr) =>
      Right(Version(
        major = majorStr.toInt, // safe, thanks to regex
        minor = Option(minorStr).getOrElse(".0").drop(1).toInt,
        patch = Option(patchStr).getOrElse(".0").drop(1).toInt))
    case _ => Left(List(regexDoesNotMatch(in)))
  }

  private def regexDoesNotMatch(in: String): ErrorMessage =
    ErrorMessage.validationFailed(in, "x.y.z", "Version")
}
