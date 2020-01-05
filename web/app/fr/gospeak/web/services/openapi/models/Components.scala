package fr.gospeak.web.services.openapi.models

import cats.data.NonEmptyList
import fr.gospeak.web.services.openapi.error.OpenApiError.ErrorMessage
import fr.gospeak.web.services.openapi.models.utils.{Reference, Schema, TODO}

/**
 * @see "https://spec.openapis.org/oas/v3.0.2#components-object"
 */
final case class Components(schemas: Option[Map[String, Schema]],
                            responses: Option[TODO],
                            parameters: Option[TODO],
                            examples: Option[TODO],
                            requestBodies: Option[TODO],
                            headers: Option[TODO],
                            securitySchemes: Option[TODO],
                            links: Option[TODO],
                            callbacks: Option[TODO],
                            extensions: Option[TODO]) {
  def getSchema(ref: Reference): Option[Schema] =
    ref.localRef
      .collect { case ("schemas", name) => schemas.getOrElse(Map()).get(name) }.flatten
      .flatMap {
        case Schema.ReferenceVal(ref) => getSchema(ref)
        case schema => Some(schema)
      }

  def hasErrors: Option[NonEmptyList[ErrorMessage]] = {
    val badExamplesInSchemaArrays = schemas.getOrElse(Map()).mapValues(_.flatten)
      .mapValues(_.collect { case Schema.ArrayVal(Schema.ReferenceVal(ref), Some(example), _) => (getSchema(ref), example) })
      .mapValues(_.collect {
        case (Some(s: Schema.StringVal), example) => (s, example.filterNot(_.isString))
        case (Some(s: Schema.IntegerVal), example) => (s, example.filterNot(_.isNumber))
        case (Some(s: Schema.NumberVal), example) => (s, example.filterNot(_.isNumber))
        case (Some(s: Schema.BooleanVal), example) => (s, example.filterNot(_.isBoolean))
        case (Some(s: Schema.ArrayVal), example) => (s, example.filterNot(_.isArray))
        case (Some(s: Schema.ObjectVal), example) => (s, example.filterNot(_.isObject))
      }).filter(_._2.exists(_._2.nonEmpty))
      .flatMap { case (name, results) => results.map { case (s, ex) => ErrorMessage.badExampleFormat(ex.map(_.value).mkString(", "), s.hint, name) } }.toList

    val duplicateSchemaNames = schemas.getOrElse(Map()).keys.groupBy(identity).filter(_._2.size > 1).keys.toList.map(ErrorMessage.duplicateValue(_, "schemas"))

    NonEmptyList.fromList(badExamplesInSchemaArrays ++ duplicateSchemaNames)
  }
}
