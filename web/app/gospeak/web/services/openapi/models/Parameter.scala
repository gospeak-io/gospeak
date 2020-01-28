package gospeak.web.services.openapi.models

import gospeak.web.services.openapi.OpenApiUtils
import gospeak.web.services.openapi.error.OpenApiError
import gospeak.web.services.openapi.models.Parameter.Location
import gospeak.web.services.openapi.models.utils.{HasValidation, Js, Markdown}

// TODO Parameter or Ref
/**
 * @see "https://spec.openapis.org/oas/v3.0.2#parameter-object"
 */
final case class Parameter(name: String,
                           in: Location,
                           deprecated: Option[Boolean],
                           description: Option[Markdown],
                           required: Option[Boolean], // required to be true when in = Path
                           allowEmptyValue: Option[Boolean],
                           style: Option[String],
                           explode: Option[Boolean],
                           allowReserved: Option[Boolean],
                           schema: Option[Schema],
                           example: Option[Js]) extends HasValidation {
  def getErrors(s: Schemas): List[OpenApiError] = {
    OpenApiUtils.validate(schema, example, s)
  }
}

object Parameter {

  sealed abstract class Location(val value: String)

  object Location {

    final case object Path extends Location("path")

    final case object Query extends Location("query")

    final case object Header extends Location("header")

    final case object Cookie extends Location("cookie")

    val all: Seq[Location] = Seq(Path, Query, Header, Cookie)

    def from(value: String): Either[OpenApiError, Location] =
      all.find(_.value == value).toRight(OpenApiError.badFormat(value, "Parameter.Location", all.map(_.value).mkString(", ")))

  }

}
