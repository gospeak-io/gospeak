package fr.gospeak.web.services.openapi.models

import cats.data.NonEmptyList
import fr.gospeak.web.services.openapi.error.OpenApiError.ErrorMessage
import fr.gospeak.web.services.openapi.models.Parameter.Location
import fr.gospeak.web.services.openapi.models.utils.{Js, Markdown}

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
                           example: Option[Js]) // TODO should match schema

object Parameter {

  sealed abstract class Location(val value: String)

  object Location {

    final case object Path extends Location("path")

    final case object Query extends Location("query")

    final case object Header extends Location("header")

    final case object Cookie extends Location("cookie")

    val all: Seq[Location] = Seq(Path, Query, Header, Cookie)

    def from(value: String): Either[NonEmptyList[ErrorMessage], Location] =
      all.find(_.value == value).toRight(NonEmptyList.of(ErrorMessage.badFormat(value, "Parameter.Location", all.map(_.value).mkString(", "))))

  }

}
