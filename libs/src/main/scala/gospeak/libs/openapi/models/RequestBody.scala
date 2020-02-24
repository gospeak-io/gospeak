package gospeak.libs.openapi.models

import gospeak.libs.openapi.models.utils.{Markdown, TODO}

// TODO RequestBody or Ref
/**
 * @see "https://spec.openapis.org/oas/v3.0.2#request-body-object"
 */
final case class RequestBody(description: Option[Markdown],
                             content: Map[String, MediaType],
                             required: Option[Boolean],
                             extensions: Option[TODO])
