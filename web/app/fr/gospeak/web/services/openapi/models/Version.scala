package fr.gospeak.web.services.openapi.models

import fr.gospeak.web.services.openapi.error.OpenApiError.Message

final case class Version(major: Int, minor: Int, patch: Int) {
  def format: String = s"$major.$minor.$patch"
}

object Version {
  private val regex = "([0-9]+)(\\.[0-9]+)?(\\.[0-9]+)?".r

  def from(in: String): Either[Seq[Message], Version] = {
    in match {
      case regex(majorStr, minorStr, patchStr) =>
        Right(Version(
          major = majorStr.toInt, // safe, thanks to regex
          minor = Option(minorStr).getOrElse(".0").drop(1).toInt,
          patch = Option(patchStr).getOrElse(".0").drop(1).toInt))
      case _ => Left(Seq(regexDoesNotMatch(in)))
    }
  }

  private def regexDoesNotMatch(in: String): Message =
    Message.validationFailed(in, "x.y.z", "Version")
}
