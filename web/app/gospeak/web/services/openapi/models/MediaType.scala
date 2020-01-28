package gospeak.web.services.openapi.models

import gospeak.web.services.openapi.OpenApiUtils
import gospeak.web.services.openapi.error.OpenApiError
import gospeak.web.services.openapi.models.utils.{HasValidation, Js, TODO}

/**
 * @see "https://spec.openapis.org/oas/v3.0.2#media-type-object"
 */
final case class MediaType(schema: Option[Schema],
                           example: Option[Js],
                           examples: Option[Map[String, TODO]],
                           encoding: Option[TODO],
                           extensions: Option[TODO]) extends HasValidation {
  def getErrors(s: Schemas): List[OpenApiError] = {
    OpenApiUtils.validate(schema, example, s)
  }
}
