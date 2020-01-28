package gospeak.web.services.openapi.models

import gospeak.web.services.openapi.models.utils.{Markdown, TODO}

/**
 * @see "https://spec.openapis.org/oas/v3.0.2#tag-object"
 */
final case class Tag(name: String,
                     description: Option[Markdown],
                     externalDocs: Option[ExternalDoc],
                     extensions: Option[TODO])
