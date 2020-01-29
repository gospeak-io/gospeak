package gospeak.libs.openapi.models

import gospeak.libs.openapi.OpenApiUtils
import gospeak.libs.openapi.error.OpenApiError
import gospeak.libs.openapi.models.utils.{HasValidation, TODO}

/**
 * @see "https://spec.openapis.org/oas/v3.0.2#components-object"
 */
final case class Components(schemas: Option[Schemas],
                            responses: Option[TODO],
                            parameters: Option[TODO],
                            examples: Option[TODO],
                            requestBodies: Option[TODO],
                            headers: Option[TODO],
                            securitySchemes: Option[TODO],
                            links: Option[TODO],
                            callbacks: Option[TODO],
                            extensions: Option[TODO]) extends HasValidation {
  def getSchema(ref: Reference): Option[Schema] =
    ref.localRef
      .collect { case ("schemas", name) => schemas.flatMap(_.get(name)) }.flatten
      .flatMap {
        case Schema.ReferenceVal(ref) => getSchema(ref)
        case schema => Some(schema)
      }

  def getErrors(s: Schemas): List[OpenApiError] = {
    val duplicateSchemaNames = schemas.map(_.value.keys).getOrElse(List()).groupBy(identity)
      .filter(_._2.size > 1).keys.toList
      .map(name => OpenApiError.duplicateValue(name).atPath(".schemas", s".$name"))
    val schemasErrors = OpenApiUtils.validate("schemas", schemas.map(_.value).getOrElse(Map()), s)
    duplicateSchemaNames ++ schemasErrors
  }
}
