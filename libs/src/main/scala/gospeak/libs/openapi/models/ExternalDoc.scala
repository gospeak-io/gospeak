package gospeak.libs.openapi.models

import gospeak.libs.openapi.models.utils.{Markdown, TODO, Url}

/**
 * @see "https://spec.openapis.org/oas/v3.0.2#external-documentation-object"
 */
final case class ExternalDoc(url: Url,
                             description: Option[Markdown],
                             extensions: Option[TODO])
