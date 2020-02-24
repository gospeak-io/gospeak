package gospeak.libs.openapi.models

import gospeak.libs.openapi.models.utils.{Markdown, TODO}

/**
 * @see "https://spec.openapis.org/oas/v3.0.2#tag-object"
 */
final case class Tag(name: String,
                     description: Option[Markdown],
                     externalDocs: Option[ExternalDoc],
                     extensions: Option[TODO])
