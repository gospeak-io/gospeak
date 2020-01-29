package gospeak.libs.openapi.models

import gospeak.libs.openapi.error.OpenApiError
import gospeak.libs.openapi.models.Path._

/**
 * @see "https://spec.openapis.org/oas/v3.0.2#paths-object"
 */
final case class Path(value: String) extends AnyVal {
  def variables: Seq[String] =
    variableRegex.findAllIn(value).toList.map(_.stripPrefix("{").stripSuffix("}"))

  def mapVariables(f: String => String): Path =
    variables.foldLeft(this) { (path, variable) => Path(path.value.replace(s"{$variable}", f(variable))) }
}

object Path {
  private val variableRegex = "\\{[^}]+}".r

  def from(value: String): Either[OpenApiError, Path] = {
    if (value.startsWith("/")) Right(new Path(value))
    else Left(OpenApiError.badFormat(value, "Path", "/..."))
  }
}
