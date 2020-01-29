package gospeak.libs.openapi.models

import gospeak.libs.openapi.models.utils.Markdown

// TODO Header or Ref
/**
 * @see "https://spec.openapis.org/oas/v3.0.2#header-object"
 */
final case class Header(description: Option[Markdown],
                        schema: Option[Schema])
