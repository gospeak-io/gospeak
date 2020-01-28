package fr.gospeak.web.services.openapi.models

import fr.gospeak.web.services.openapi.models.utils.Markdown

// TODO Header or Ref
/**
 * @see "https://spec.openapis.org/oas/v3.0.2#header-object"
 */
final case class Header(description: Option[Markdown],
                        schema: Option[Schema])
