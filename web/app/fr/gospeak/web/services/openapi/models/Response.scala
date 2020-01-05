package fr.gospeak.web.services.openapi.models

import fr.gospeak.web.services.openapi.models.utils.{Markdown, TODO}

// TODO Response or Ref
/**
 * @see "https://spec.openapis.org/oas/v3.0.2#responses-object"
 * @see "https://spec.openapis.org/oas/v3.0.2#response-object"
 */
final case class Response(description: Markdown,
                          headers: Option[Map[String, Header]],
                          content: Option[Map[String, MediaType]],
                          links: Option[Map[String, Link]],
                          extensions: Option[TODO])
