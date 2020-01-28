package fr.gospeak.web.services.openapi.models

import fr.gospeak.web.services.openapi.OpenApiUtils
import fr.gospeak.web.services.openapi.error.OpenApiError
import fr.gospeak.web.services.openapi.models.utils.{HasValidation, Markdown, TODO}

// TODO Response or Ref
/**
 * @see "https://spec.openapis.org/oas/v3.0.2#responses-object"
 * @see "https://spec.openapis.org/oas/v3.0.2#response-object"
 */
final case class Response(description: Markdown,
                          headers: Option[Map[String, Header]],
                          content: Option[Map[String, MediaType]],
                          links: Option[Map[String, Link]],
                          extensions: Option[TODO]) extends HasValidation {
  def getErrors(s: Schemas): List[OpenApiError] = {
    val contentErrors = OpenApiUtils.validate("content", content.getOrElse(Map()), s)
    contentErrors
  }
}
