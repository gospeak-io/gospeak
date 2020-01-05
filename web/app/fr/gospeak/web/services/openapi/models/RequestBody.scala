package fr.gospeak.web.services.openapi.models

import fr.gospeak.web.services.openapi.models.utils.{Markdown, TODO}

// TODO RequestBody or Ref
/**
 * @see "https://spec.openapis.org/oas/v3.0.2#request-body-object"
 */
final case class RequestBody(description: Option[Markdown],
                             content: Map[String, MediaType],
                             required: Option[Boolean],
                             extensions: Option[TODO])
